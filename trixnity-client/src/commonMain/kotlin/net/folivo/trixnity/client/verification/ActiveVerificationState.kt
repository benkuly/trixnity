package net.folivo.trixnity.client.verification

import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.key.verification.*

sealed interface ActiveVerificationState {
    data class Request(
        val content: VerificationRequest,
        private val ownDeviceId: String,
        private val supportedMethods: Set<VerificationMethod>,
        private val relatesTo: VerificationStepRelatesTo?,
        private val transactionId: String?,
        private val send: suspend (VerificationStep) -> Unit
    ) : ActiveVerificationState {
        suspend fun ready() {
            send(ReadyEventContent(ownDeviceId, supportedMethods, relatesTo, transactionId))
        }
    }

    data class Ready(
        private val ownDeviceId: String,
        val methods: Set<VerificationMethod>,
        private val relatesTo: VerificationStepRelatesTo?,
        private val transactionId: String?,
        private val send: suspend (VerificationStep) -> Unit
    ) : ActiveVerificationState {
        suspend fun start(method: VerificationMethod) {
            val content = when (method) {
                is VerificationMethod.Sas -> StartEventContent.SasStartEventContent(
                    fromDevice = ownDeviceId,
                    relatesTo = relatesTo,
                    transactionId = transactionId
                )
                is VerificationMethod.Unknown -> throw IllegalArgumentException("method should never be unknown")
            }
            send(content)
        }
    }

    data class Start(
        val method: ActiveVerificationMethod,
        val senderUserId: UserId,
        val senderDeviceId: String,
    ) : ActiveVerificationState

    data class PartlyDone(val isOurOwn: Boolean) : ActiveVerificationState

    object Done : ActiveVerificationState
    data class Cancel(val content: CancelEventContent, val sender: UserId) : ActiveVerificationState
}