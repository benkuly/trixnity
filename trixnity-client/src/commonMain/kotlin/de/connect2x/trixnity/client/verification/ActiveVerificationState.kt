package de.connect2x.trixnity.client.verification

import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.key.verification.*
import de.connect2x.trixnity.core.model.events.m.key.verification.SasKeyAgreementProtocol.Curve25519HkdfSha256
import de.connect2x.trixnity.core.model.events.m.key.verification.SasMessageAuthenticationCode.HkdfHmacSha256
import de.connect2x.trixnity.core.model.events.m.key.verification.SasMessageAuthenticationCode.HkdfHmacSha256V2

sealed interface ActiveVerificationState {

    /**
     * This state is active when we started the request.
     */
    data class OwnRequest(
        val content: VerificationRequest,
    ) : ActiveVerificationState

    /**
     * This state is active when another device or user started the request.
     */
    data class TheirRequest(
        val content: VerificationRequest,
        private val ownDeviceId: String,
        private val supportedMethods: Set<VerificationMethod>,
        private val relatesTo: RelatesTo.Reference?,
        private val transactionId: String?,
        private val send: suspend (VerificationStep) -> Unit
    ) : ActiveVerificationState {
        suspend fun ready() {
            send(VerificationReadyEventContent(ownDeviceId, supportedMethods, relatesTo, transactionId))
        }
    }

    /**
     * This state is active when the request is accepted.
     */
    data class Ready(
        private val ownDeviceId: String,
        val methods: Set<VerificationMethod>,
        private val relatesTo: RelatesTo.Reference?,
        private val transactionId: String?,
        private val send: suspend (VerificationStep) -> Unit
    ) : ActiveVerificationState {
        suspend fun start(method: VerificationMethod) {
            val content = when (method) {
                is VerificationMethod.Sas -> VerificationStartEventContent.SasStartEventContent(
                    fromDevice = ownDeviceId,
                    relatesTo = relatesTo,
                    hashes = setOf(SasHash.Sha256),
                    keyAgreementProtocols = setOf(Curve25519HkdfSha256),
                    messageAuthenticationCodes = setOf(HkdfHmacSha256, HkdfHmacSha256V2),
                    shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                    transactionId = transactionId
                )

                is VerificationMethod.Unknown -> throw IllegalArgumentException("method should never be unknown")
            }
            send(content)
        }
    }

    /**
     * This state is active when the devices agreed on a verification method. It contains a sub-state.
     */
    data class Start(
        val method: ActiveVerificationMethod,
        val senderUserId: UserId,
        val senderDeviceId: String,
    ) : ActiveVerificationState

    /**
     * This state is active when one device is done.
     */
    data class WaitForDone(val isOurOwn: Boolean) : ActiveVerificationState

    /**
     * This state is active when the verification is done.
     */
    data object Done : ActiveVerificationState

    /**
     * This state is active when the verification is cancelled.
     */
    data class Cancel(val content: VerificationCancelEventContent, val isOurOwn: Boolean) : ActiveVerificationState

    /**
     * This state is active when another own device accepted the request.
     */
    data object AcceptedByOtherDevice : ActiveVerificationState

    /**
     * This state is active when an incoming request was accepted, but the state got missing (e.g. by restarting the App)
     */
    data object Undefined : ActiveVerificationState
}