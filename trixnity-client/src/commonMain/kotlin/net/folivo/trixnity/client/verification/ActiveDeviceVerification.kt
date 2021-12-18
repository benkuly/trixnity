package net.folivo.trixnity.client.verification

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.verification.ActiveVerificationState.Cancel
import net.folivo.trixnity.client.verification.ActiveVerificationState.Done
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.key.verification.CancelEventContent.Code.Timeout
import net.folivo.trixnity.core.model.events.m.key.verification.RequestEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStep
import net.folivo.trixnity.olm.OlmLibraryException
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class ActiveDeviceVerification(
    request: RequestEventContent,
    requestIsOurs: Boolean,
    ownUserId: UserId,
    ownDeviceId: String,
    theirUserId: UserId,
    theirDeviceId: String,
    supportedMethods: Set<VerificationMethod>,
    private val api: MatrixApiClient,
    private val olm: OlmService,
    key: KeyService,
    store: Store,
    loggerFactory: LoggerFactory
) : ActiveVerification(
    request,
    requestIsOurs,
    ownUserId,
    ownDeviceId,
    theirUserId,
    theirDeviceId,
    request.timestamp,
    supportedMethods,
    null,
    request.transactionId,
    store,
    key,
    api.json,
    loggerFactory
) {
    private val log = newLogger(loggerFactory)

    override suspend fun sendVerificationStep(step: VerificationStep) {
        log.debug { "send verification step $step" }
        val theirDeviceId = this.theirDeviceId
        requireNotNull(theirDeviceId) { "their device id should never be null" }
        val sendContent = try {
            olm.events.encryptOlm(step, theirUserId, theirDeviceId)
        } catch (olmError: OlmLibraryException) {
            step
        }
        api.users.sendToDevice(mapOf(theirUserId to mapOf(theirDeviceId to sendContent)))
    }

    override suspend fun lifecycle(scope: CoroutineScope) {
        api.sync.subscribe(::handleVerificationStepEvents)
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        val job = scope.launch(start = UNDISPATCHED) {
            olm.decryptedOlmEvents.collect(::handleOlmDecryptedVerificationRequestEvents)
        }
        scope.launch(start = UNDISPATCHED) {
            // we do this, because otherwise the timeline job could run infinite, when no new timeline event arrives
            while (isVerificationRequestActive(timestamp, state.value)) {
                delay(500)
            }
            log.debug { "stop verification request lifecycle" }
            job.cancel()
            api.sync.unsubscribe(::handleVerificationStepEvents)
            if (state.value !is Cancel && state.value !is Done && !isVerificationRequestActive(timestamp)) {
                cancel(Timeout, "verification timed out")
            }
        }
    }

    private suspend fun handleVerificationStepEvents(event: Event<VerificationStep>) {
        if (event is Event.ToDeviceEvent) handleVerificationStepEvent(event.content, event.sender)
    }

    private suspend fun handleOlmDecryptedVerificationRequestEvents(
        event: OlmService.DecryptedOlmEvent,
    ) {
        val content = event.decrypted.content
        if (content is VerificationStep) handleVerificationStepEvent(content, event.decrypted.sender)
    }

    private suspend fun handleVerificationStepEvent(step: VerificationStep, sender: UserId) {
        val eventTransactionId = step.transactionId
        if (eventTransactionId != null && eventTransactionId == transactionId
            && isVerificationRequestActive(timestamp, state.value)
        ) {
            handleIncomingVerificationStep(step, sender, false)
        }
    }
}