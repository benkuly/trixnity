package net.folivo.trixnity.client.verification

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import net.folivo.trixnity.client.key.KeyTrustService
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.key.verification.*
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.Accepted
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.Timeout
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribe
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventContainer
import net.folivo.trixnity.crypto.olm.OlmDecrypter
import net.folivo.trixnity.crypto.olm.OlmEncryptionService

private val log = KotlinLogging.logger {}

class ActiveDeviceVerification(
    request: VerificationRequestEventContent,
    requestIsOurs: Boolean,
    ownUserId: UserId,
    ownDeviceId: String,
    theirUserId: UserId,
    theirDeviceId: String? = null,
    private val theirDeviceIds: Set<String> = setOf(),
    supportedMethods: Set<VerificationMethod>,
    private val api: MatrixClientServerApiClient,
    private val olmDecrypter: OlmDecrypter,
    private val olmEncryptionService: OlmEncryptionService,
    keyTrust: KeyTrustService,
    keyStore: KeyStore,
) : ActiveVerificationImpl(
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
    keyStore,
    keyTrust,
    api.json,
) {
    override suspend fun sendVerificationStep(step: VerificationStep) {
        log.debug { "send verification step $step" }
        val theirDeviceId = this.theirDeviceId
        val theirDeviceIds =
            if (theirDeviceId == null && step is VerificationCancelEventContent) theirDeviceIds
            else setOfNotNull(theirDeviceId)

        if (theirDeviceIds.isNotEmpty())
            api.users.sendToDevice(mapOf(theirUserId to theirDeviceIds.associateWith {
                try {
                    olmEncryptionService.encryptOlm(step, theirUserId, it)
                } catch (error: Exception) {
                    log.debug { "could not encrypt verification step. will be send unencrypted. Reason: ${error.message}" }
                    step
                }
            })).getOrThrow()
    }

    override suspend fun lifecycle() {
        try {
            api.sync.subscribe(::handleVerificationStepEvents)
            olmDecrypter.subscribe(::handleOlmDecryptedVerificationRequestEvents)
            // we do this, because otherwise the timeline job could run infinite, when no new timeline event arrives
            while (isVerificationRequestActive(timestamp, state.value)) {
                delay(500)
            }
            if (isVerificationTimedOut(timestamp, state.value)) {
                cancel(Timeout, "verification timed out")
            }
        } finally {
            api.sync.unsubscribe(::handleVerificationStepEvents)
            olmDecrypter.unsubscribe(::handleOlmDecryptedVerificationRequestEvents)
        }
    }

    private suspend fun handleVerificationStepEvents(event: Event<VerificationStep>) {
        if (event is Event.ToDeviceEvent) handleVerificationStepEvent(event.content, event.sender)
    }

    private suspend fun handleOlmDecryptedVerificationRequestEvents(event: DecryptedOlmEventContainer) {
        val content = event.decrypted.content
        if (content is VerificationStep) handleVerificationStepEvent(content, event.decrypted.sender)
    }

    private suspend fun handleVerificationStepEvent(step: VerificationStep, sender: UserId) {
        val eventTransactionId = step.transactionId
        if (eventTransactionId != null && eventTransactionId == transactionId
            && isVerificationRequestActive(timestamp, state.value)
        ) {
            if (step is VerificationReadyEventContent) {
                val cancelDeviceIds = theirDeviceIds - step.fromDevice
                if (cancelDeviceIds.isNotEmpty()) {
                    val cancelEvent =
                        VerificationCancelEventContent(Accepted, "accepted by other device", relatesTo, transactionId)
                    try {
                        api.users.sendToDevice(mapOf(theirUserId to cancelDeviceIds.associateWith {
                            try {
                                olmEncryptionService.encryptOlm(cancelEvent, theirUserId, it)
                            } catch (exception: Exception) {
                                log.debug { "could not encrypt verification step. will be send unencrypted. Reason: ${exception.message}" }
                                cancelEvent
                            }
                        })).getOrThrow()
                    } catch (error: Exception) {
                        log.warn { "could not send cancel to other device ids ($cancelDeviceIds)" }
                    }
                }
            }
            handleIncomingVerificationStep(step, sender, false)
        }
    }
}