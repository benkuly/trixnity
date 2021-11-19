package net.folivo.trixnity.client.verification

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.possiblyEncryptEvent
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.client.verification.ActiveUserVerification.VerificationStepSearchResult.*
import net.folivo.trixnity.client.verification.ActiveVerificationState.Cancel
import net.folivo.trixnity.client.verification.ActiveVerificationState.Done
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.key.verification.CancelEventContent.Code
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStep
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStepRelatesTo
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.VerificationRequestMessageEventContent
import net.folivo.trixnity.olm.OlmLibraryException
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class ActiveUserVerification(
    request: VerificationRequestMessageEventContent,
    val requestEventId: EventId,
    requestTimestamp: Long,
    ownUserId: UserId,
    ownDeviceId: String,
    theirUserId: UserId,
    theirInitialDeviceId: String?,
    val roomId: RoomId,
    supportedMethods: Set<VerificationMethod>,
    private val api: MatrixApiClient,
    store: Store,
    private val olm: OlmService,
    private val user: UserService,
    private val room: RoomService,
    loggerFactory: LoggerFactory
) : ActiveVerification(
    request,
    ownUserId,
    ownDeviceId,
    theirUserId,
    theirInitialDeviceId,
    requestTimestamp,
    supportedMethods,
    VerificationStepRelatesTo(requestEventId),
    null,
    store,
    api.json,
    loggerFactory
) {
    private val log = newLogger(loggerFactory)

    override suspend fun sendVerificationStep(step: VerificationStep) {
        log.debug { "send verification step $step" }
        val sendContent = try {
            possiblyEncryptEvent(step, roomId, store, olm, user)
        } catch (olmError: OlmLibraryException) {
            step
        }
        api.rooms.sendMessageEvent(roomId, sendContent)
    }

    private sealed interface VerificationStepSearchResult {
        object NoVerificationStep : VerificationStepSearchResult
        object MaybeVerificationStep : VerificationStepSearchResult
        data class IsVerificationStep(
            val stepContent: VerificationStep, val sender: UserId
        ) : VerificationStepSearchResult
    }

    override suspend fun lifecycle(scope: CoroutineScope) {
        val timelineJob = scope.launch {
            var currentTimelineEvent: StateFlow<TimelineEvent?> =
                room.getTimelineEvent(requestEventId, roomId, this)
            while (isVerificationRequestActive(timestamp, state.value)) {
                currentTimelineEvent = currentTimelineEvent
                    .filterNotNull()
                    .map { room.getNextTimelineEvent(it, this) }
                    .filterNotNull()
                    .first()

                val searchResult = currentTimelineEvent.filterNotNull().map {
                    when (val eventContent = it.event.content) {
                        is VerificationStep -> IsVerificationStep(eventContent, it.event.sender)
                        is EncryptedEventContent -> {
                            when (val decryptedEventContent = it.decryptedEvent?.getOrNull()?.content) {
                                null -> MaybeVerificationStep
                                is VerificationStep -> IsVerificationStep(decryptedEventContent, it.event.sender)
                                else -> NoVerificationStep
                            }
                        }
                        else -> NoVerificationStep
                    }
                }.first { it is IsVerificationStep || it is NoVerificationStep }
                if (searchResult is IsVerificationStep) {
                    // we just ignore our own events (we already processed them)
                    if (searchResult.sender != ownUserId && searchResult.stepContent.relatesTo == relatesTo)
                        handleIncomingVerificationStep(searchResult.stepContent, searchResult.sender)
                }
            }
        }
        scope.launch {
            // we do this, because otherwise the timeline job could run infinite, when no new timeline event arrives
            while (isVerificationRequestActive(timestamp, state.value)) {
                delay(500)
            }
            timelineJob.cancel()

            if (state.value !is Cancel && state.value !is Done && !isVerificationRequestActive(timestamp)) {
                cancel(Code.Timeout, "verification timed out")
            }
        }
    }
}