package net.folivo.trixnity.client.verification

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import net.folivo.trixnity.client.key.KeyTrustService
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import net.folivo.trixnity.core.model.events.m.key.verification.*
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.Accepted
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.Timeout
import net.folivo.trixnity.core.subscribeContent
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventContainer
import net.folivo.trixnity.crypto.olm.OlmDecrypter
import net.folivo.trixnity.crypto.olm.OlmEncryptionService
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger("net.folivo.trixnity.client.verification.ActiveDeviceVerification")

interface ActiveDeviceVerification : ActiveVerification
class ActiveDeviceVerificationImpl(
    request: VerificationRequestToDeviceEventContent,
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
    private val clock: Clock,
) : ActiveDeviceVerification, ActiveVerificationImpl(
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
            api.user.sendToDevice(mapOf(theirUserId to theirDeviceIds.associateWith {
                olmEncryptionService.encryptOlm(step, theirUserId, it).getOrNull()
                    ?: step
            })).getOrThrow()
    }

    override suspend fun lifecycle() {
        val unsubscribeHandleVerificationStepEvents =
            api.sync.subscribeContent(subscriber = ::handleVerificationStepEvents)
        val unsubscribeHandleOlmDecryptedVerificationRequestEvents =
            olmDecrypter.subscribe(::handleOlmDecryptedVerificationRequestEvents)
        try {
            // we do this, because otherwise the timeline job could run infinite, when no new timeline event arrives
            while (isVerificationRequestActive(timestamp, clock, state.value)) {
                delay(1.seconds)
            }
            if (isVerificationTimedOut(timestamp, clock, state.value)) {
                cancel(Timeout, "verification timed out")
            }
        } finally {
            unsubscribeHandleVerificationStepEvents()
            unsubscribeHandleOlmDecryptedVerificationRequestEvents()
        }
    }

    private suspend fun handleVerificationStepEvents(event: ClientEvent<VerificationStep>) {
        if (event is ToDeviceEvent) handleVerificationStepEvent(event.content, event.sender)
    }

    private suspend fun handleOlmDecryptedVerificationRequestEvents(event: DecryptedOlmEventContainer) {
        val content = event.decrypted.content
        if (content is VerificationStep) handleVerificationStepEvent(content, event.decrypted.sender)
    }

    private suspend fun handleVerificationStepEvent(step: VerificationStep, sender: UserId) {
        val eventTransactionId = step.transactionId
        if (eventTransactionId != null && eventTransactionId == transactionId
            && isVerificationRequestActive(timestamp, clock, state.value)
        ) {
            if (step is VerificationReadyEventContent) {
                val cancelDeviceIds = theirDeviceIds - step.fromDevice
                if (cancelDeviceIds.isNotEmpty()) {
                    val cancelEvent =
                        VerificationCancelEventContent(Accepted, "accepted by other device", relatesTo, transactionId)
                    try {
                        api.user.sendToDevice(mapOf(theirUserId to cancelDeviceIds.associateWith {
                            olmEncryptionService.encryptOlm(cancelEvent, theirUserId, it).getOrNull() ?: cancelEvent
                        })).getOrThrow()
                    } catch (error: Exception) {
                        log.warn { "could not send cancel to other device ids ($cancelDeviceIds)" }
                    }
                }
            }
            handleVerificationStep(step, sender, false)
        }
    }
}