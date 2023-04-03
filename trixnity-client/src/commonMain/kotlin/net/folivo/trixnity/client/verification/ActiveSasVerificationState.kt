package net.folivo.trixnity.client.verification

import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.key.getAllKeysFromUser
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.RelatesTo
import net.folivo.trixnity.core.model.events.m.key.verification.*
import net.folivo.trixnity.core.model.events.m.key.verification.SasHash.Sha256
import net.folivo.trixnity.core.model.events.m.key.verification.SasKeyAgreementProtocol.Curve25519HkdfSha256
import net.folivo.trixnity.core.model.events.m.key.verification.SasMessageAuthenticationCode.HkdfHmacSha256
import net.folivo.trixnity.core.model.events.m.key.verification.SasMessageAuthenticationCode.HkdfHmacSha256V2
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.*
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStartEventContent.SasStartEventContent
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage.MasterKey
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.model.keys.Keys
import net.folivo.trixnity.olm.OlmSAS

sealed interface ActiveSasVerificationState {
    data class OwnSasStart(
        val content: SasStartEventContent
    ) : ActiveSasVerificationState

    data class TheirSasStart(
        val content: SasStartEventContent,
        private val olmSas: OlmSAS,
        private val json: Json,
        private val relatesTo: RelatesTo.Reference?,
        private val transactionId: String?,
        private val send: suspend (step: VerificationStep) -> Unit
    ) : ActiveSasVerificationState {
        suspend fun accept() {
            when {
                content.hashes.contains(Sha256).not() -> {
                    send(
                        VerificationCancelEventContent(
                            UnknownMethod,
                            "only hashes [${Sha256.name}] are supported",
                            relatesTo,
                            transactionId
                        )
                    )
                }

                content.keyAgreementProtocols.contains(Curve25519HkdfSha256).not() -> {
                    send(
                        VerificationCancelEventContent(
                            UnknownMethod,
                            "only key agreement protocols [${Curve25519HkdfSha256.name}] are supported",
                            relatesTo,
                            transactionId
                        )
                    )
                }

                content.messageAuthenticationCodes.contains(HkdfHmacSha256).not()
                        && content.messageAuthenticationCodes.contains(HkdfHmacSha256V2).not() -> {
                    send(
                        VerificationCancelEventContent(
                            UnknownMethod,
                            "only message authentication codes [${HkdfHmacSha256.name} ${HkdfHmacSha256V2.name}] are supported",
                            relatesTo,
                            transactionId
                        )
                    )
                }

                content.shortAuthenticationString.contains(SasMethod.Decimal).not()
                        && content.shortAuthenticationString.contains(SasMethod.Emoji).not() -> {
                    send(
                        VerificationCancelEventContent(
                            UnknownMethod,
                            "only short authentication strings [${SasMethod.Decimal.name} ${SasMethod.Emoji.name}] are supported",
                            relatesTo,
                            transactionId
                        )
                    )
                }

                else -> {
                    send(
                        SasAcceptEventContent(
                            commitment = createSasCommitment(olmSas.publicKey, content, json),
                            hash = Sha256,
                            keyAgreementProtocol = Curve25519HkdfSha256,
                            messageAuthenticationCode = content.messageAuthenticationCodes.let {
                                if (it.contains(HkdfHmacSha256V2)) HkdfHmacSha256V2
                                else HkdfHmacSha256
                            },
                            shortAuthenticationString = setOf(SasMethod.Decimal, SasMethod.Emoji),
                            relatesTo = relatesTo,
                            transactionId = transactionId
                        )
                    )
                }
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
        private val messageAuthenticationCode: SasMessageAuthenticationCode,
        private val relatesTo: RelatesTo.Reference?,
        private val transactionId: String?,
        private val olmSas: OlmSAS,
        private val keyStore: KeyStore,
        private val send: suspend (stepContent: VerificationStep) -> Unit,
    ) : ActiveSasVerificationState {
        private val actualTransactionId = relatesTo?.eventId?.full
            ?: transactionId
            ?: throw IllegalArgumentException("actualTransactionId should never be null")

        suspend fun match() {
            when (messageAuthenticationCode) {
                HkdfHmacSha256 -> sendHkdfHmacSha256Mac(olmSas::calculateMac)
                HkdfHmacSha256V2 -> sendHkdfHmacSha256Mac(olmSas::calculateMacFixedBase64)

                is SasMessageAuthenticationCode.Unknown -> {
                    send(
                        VerificationCancelEventContent(
                            UnknownMethod,
                            "message authentication code not supported",
                            relatesTo,
                            transactionId
                        )
                    )
                }
            }
        }

        private suspend fun sendHkdfHmacSha256Mac(calculateMac: (String, String) -> String) {
            val baseInfo = "MATRIX_KEY_VERIFICATION_MAC" +
                    ownUserId.full + ownDeviceId +
                    theirUserId.full + theirDeviceId +
                    actualTransactionId
            val keysToMac = keyStore.getAllKeysFromUser<Ed25519Key>(ownUserId, ownDeviceId, MasterKey)
            if (keysToMac.isNotEmpty()) {
                val keys =
                    calculateMac(
                        keysToMac.map { it.fullKeyId }.sortedBy { it }.joinToString(","),
                        baseInfo + "KEY_IDS"
                    )
                val macs =
                    keysToMac.map { it.copy(value = calculateMac(it.value, baseInfo + it.fullKeyId)) }
                send(SasMacEventContent(keys, Keys(macs.toSet()), relatesTo, transactionId))
            } else send(
                VerificationCancelEventContent(
                    InternalError,
                    "no keys found",
                    relatesTo,
                    transactionId
                )
            )
        }


        suspend fun noMatch() {
            send(VerificationCancelEventContent(MismatchedSas, "no match of SAS", relatesTo, transactionId))
        }
    }

    object WaitForMacs : ActiveSasVerificationState
}