package de.connect2x.trixnity.client.verification

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.key.KeyTrustService
import de.connect2x.trixnity.client.room.RoomService
import de.connect2x.trixnity.client.store.KeyStore
import de.connect2x.trixnity.client.verification.ActiveUserVerificationImpl.VerificationStepSearchResult.*
import de.connect2x.trixnity.client.verification.ActiveVerificationState.*
import de.connect2x.trixnity.clientserverapi.model.room.GetEvents.Direction.FORWARDS
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationMethod
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationReadyEventContent
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationStep
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent.VerificationRequest
import de.connect2x.trixnity.crypto.driver.CryptoDriver
import kotlin.time.Clock

private val log = KotlinLogging.logger("de.connect2x.trixnity.client.verification.ActiveUserVerification")

interface ActiveUserVerification : ActiveVerification {
    val requestEventId: EventId
    val roomId: RoomId
}

class ActiveUserVerificationImpl(
    request: VerificationRequest,
    private val requestIsFromOurOwn: Boolean,
    override val requestEventId: EventId,
    requestTimestamp: Long,
    ownUserId: UserId,
    ownDeviceId: String,
    theirUserId: UserId,
    theirInitialDeviceId: String?,
    override val roomId: RoomId,
    supportedMethods: Set<VerificationMethod>,
    json: Json,
    keyStore: KeyStore,
    private val room: RoomService,
    keyTrust: KeyTrustService,
    private val clock: Clock,
    driver: CryptoDriver,
) : ActiveUserVerification, ActiveVerificationImpl(
    request,
    requestIsFromOurOwn,
    ownUserId,
    ownDeviceId,
    theirUserId,
    theirInitialDeviceId,
    requestTimestamp,
    supportedMethods,
    RelatesTo.Reference(requestEventId),
    null,
    keyStore,
    keyTrust,
    json,
    driver,
) {
    override suspend fun sendVerificationStep(step: VerificationStep) {
        log.debug { "send verification step $step" }
        room.sendMessage(roomId) { content(step) }
    }

    private sealed interface VerificationStepSearchResult {
        data object NoVerificationStep : VerificationStepSearchResult
        data object MaybeVerificationStep : VerificationStepSearchResult
        data class IsVerificationStep(
            val stepContent: VerificationStep, val sender: UserId
        ) : VerificationStepSearchResult
    }

    override suspend fun lifecycle() = coroutineScope {
        val timelineJob = launch {
            room.getTimelineEvents(roomId, requestEventId, FORWARDS)
                .collect { timelineEvent ->
                    val searchResult = timelineEvent.filterNotNull().map {
                        val contentResult = it.content
                        val contentValue = contentResult?.getOrNull()
                        when {
                            contentResult == null -> MaybeVerificationStep // this allows us to wait for decryption
                            contentValue is VerificationStep -> IsVerificationStep(contentValue, it.event.sender)
                            else -> NoVerificationStep
                        }
                    }.first { it is IsVerificationStep || it is NoVerificationStep }
                    if (searchResult is IsVerificationStep && searchResult.stepContent.relatesTo == relatesTo) {
                        val stepContent = searchResult.stepContent
                        when {
                            !requestIsFromOurOwn
                                    && searchResult.sender == ownUserId
                                    && stepContent is VerificationReadyEventContent -> {
                                if (stepContent.fromDevice != ownDeviceId)
                                    mutableState.value = AcceptedByOtherDevice
                                else if (state.value !is Ready)
                                    mutableState.value = Undefined
                            }

                            state.value == AcceptedByOtherDevice || state.value == Undefined -> {}
                            // ignore own events (we already processed them)
                            searchResult.sender != ownUserId -> handleVerificationStep(
                                searchResult.stepContent,
                                searchResult.sender,
                                searchResult.sender == ownUserId
                            )
                        }
                    }
                }
        }
        // we do this, because otherwise the timeline job could run infinite, when no new timeline event arrives
        while (isVerificationRequestActive(timestamp, clock, state.value)) {
            delay(500)
        }
        timelineJob.cancel()

        if (isVerificationTimedOut(timestamp, clock, state.value)) {
            cancel(Code.Timeout, "verification timed out")
        }
    }
}