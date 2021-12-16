package net.folivo.trixnity.client.verification

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.crypto.getKeysFromUser
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.Key.Ed25519Key
import net.folivo.trixnity.core.model.crypto.Keys
import net.folivo.trixnity.core.model.events.m.key.verification.*
import net.folivo.trixnity.core.model.events.m.key.verification.CancelEventContent.Code.*
import net.folivo.trixnity.core.model.events.m.key.verification.StartEventContent.SasStartEventContent
import net.folivo.trixnity.olm.OlmSAS

sealed interface ActiveSasVerificationState {
    data class OwnSasStart(
        val content: SasStartEventContent
    ) : ActiveSasVerificationState

    data class TheirSasStart(
        val content: SasStartEventContent,
        private val olmSas: OlmSAS,
        private val json: Json,
        private val relatesTo: VerificationStepRelatesTo?,
        private val transactionId: String?,
        private val send: suspend (step: VerificationStep) -> Unit
    ) : ActiveSasVerificationState {
        @OptIn(ExperimentalSerializationApi::class)
        suspend fun accept() {
            if (content.hashes.contains("sha256")) {
                val commitment = createSasCommitment(olmSas.publicKey, content, json)
                send(SasAcceptEventContent(commitment, relatesTo = relatesTo, transactionId = transactionId))
            } else {
                send(CancelEventContent(UnknownMethod, "we only support sha256", relatesTo, transactionId))
            }
        }
    }

    data class Accept(val isOurOwn: Boolean) : ActiveSasVerificationState
    data class WaitForKeys(val isOurOwn: Boolean) : ActiveSasVerificationState
    data class ComparisonByUser(
        val decimal: List<Int>,
        val emojis: List<Pair<Int, String>>,
        private val ownUserId: UserId,
        private val ownDeviceId: String,
        private val theirUserId: UserId,
        private val theirDeviceId: String,
        private val messageAuthenticationCode: String,
        private val relatesTo: VerificationStepRelatesTo?,
        private val transactionId: String?,
        private val olmSas: OlmSAS,
        private val store: Store,
        private val send: suspend (stepContent: VerificationStep) -> Unit,
    ) : ActiveSasVerificationState {
        private val actualTransactionId = relatesTo?.eventId?.full
            ?: transactionId
            ?: throw IllegalArgumentException("actualTransactionId should never be null")

        suspend fun match() {
            if (messageAuthenticationCode == "hkdf-hmac-sha256") {
                val baseInfo = "MATRIX_KEY_VERIFICATION_MAC" +
                        ownUserId + ownDeviceId +
                        theirUserId + theirDeviceId +
                        actualTransactionId
                val keysToMac = store.keys.getKeysFromUser<Ed25519Key>(ownUserId)
                if (keysToMac != null) {
                    val keys = olmSas.calculateMac(
                        keysToMac.map { it.fullKeyId }.sortedBy { it }.joinToString(","),
                        baseInfo + "KEY_IDS"
                    )
                    val macs = keysToMac.map { it.copy(value = olmSas.calculateMac(it.value, baseInfo + it.fullKeyId)) }
                    send(SasMacEventContent(keys, Keys(macs.toSet()), relatesTo, transactionId))
                } else send(CancelEventContent(InternalError, "no keys found", relatesTo, transactionId))
            } else send(
                CancelEventContent(UnknownMethod, "message authentication code not supported", relatesTo, transactionId)
            )
        }


        suspend fun noMatch() {
            send(CancelEventContent(MismatchedSas, "no match of SAS", relatesTo, transactionId))
        }
    }

    data class WaitForMacs(val isOurOwn: Boolean) : ActiveSasVerificationState
}