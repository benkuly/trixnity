package net.folivo.trixnity.client.verification

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mu.KotlinLogging
import net.folivo.trixnity.client.crypto.PossiblyEncryptEvent
import net.folivo.trixnity.client.key.KeyTrustService
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.client.verification.ActiveUserVerification.VerificationStepSearchResult.*
import net.folivo.trixnity.client.verification.ActiveVerificationState.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.rooms.GetEvents.Direction.FORWARDS
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.RelatesTo
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationReadyEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStep
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.VerificationRequestMessageEventContent

private val log = KotlinLogging.logger {}

class ActiveUserVerification(
    request: VerificationRequestMessageEventContent,
    private val requestIsFromOurOwn: Boolean,
    val requestEventId: EventId,
    requestTimestamp: Long,
    ownUserId: UserId,
    ownDeviceId: String,
    theirUserId: UserId,
    theirInitialDeviceId: String?,
    val roomId: RoomId,
    supportedMethods: Set<VerificationMethod>,
    private val api: MatrixClientServerApiClient,
    private val possiblyEncryptEvent: PossiblyEncryptEvent,
    keyStore: KeyStore,
    private val room: RoomService,
    keyTrust: KeyTrustService,
) : ActiveVerificationImpl(
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
    api.json,
) {
    override fun theirDeviceId(): String? = theirDeviceId
    override suspend fun sendVerificationStep(step: VerificationStep) {
        log.debug { "send verification step $step" }
        val sendContent = possiblyEncryptEvent(step, roomId)
            .onFailure { log.debug { "could not encrypt verification step. will be send unencrypted. Reason: ${it.message}" } }
            .getOrNull() ?: step
        api.rooms.sendMessageEvent(roomId, sendContent).getOrThrow()
    }

    private sealed interface VerificationStepSearchResult {
        object NoVerificationStep : VerificationStepSearchResult
        object MaybeVerificationStep : VerificationStepSearchResult
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
                            searchResult.sender != ownUserId -> handleIncomingVerificationStep(
                                searchResult.stepContent,
                                searchResult.sender,
                                searchResult.sender == ownUserId
                            )
                        }
                    }
                }
        }
        // we do this, because otherwise the timeline job could run infinite, when no new timeline event arrives
        while (isVerificationRequestActive(timestamp, state.value)) {
            delay(500)
        }
        timelineJob.cancel()

        if (isVerificationTimedOut(timestamp, state.value)) {
            cancel(Code.Timeout, "verification timed out")
        }
    }
}