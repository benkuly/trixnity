package de.connect2x.trixnity.client.verification

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.key.getAllKeysFromUser
import de.connect2x.trixnity.client.store.KeyStore
import de.connect2x.trixnity.core.model.keys.MacValue
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.key.verification.*
import de.connect2x.trixnity.core.model.events.m.key.verification.SasHash.Sha256
import de.connect2x.trixnity.core.model.events.m.key.verification.SasKeyAgreementProtocol.Curve25519HkdfSha256
import de.connect2x.trixnity.core.model.events.m.key.verification.SasMessageAuthenticationCode.HkdfHmacSha256
import de.connect2x.trixnity.core.model.events.m.key.verification.SasMessageAuthenticationCode.HkdfHmacSha256V2
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.*
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationStartEventContent.SasStartEventContent
import de.connect2x.trixnity.core.model.keys.CrossSigningKeysUsage.MasterKey
import de.connect2x.trixnity.core.model.keys.Key.Ed25519Key
import de.connect2x.trixnity.core.model.keys.KeyValue
import de.connect2x.trixnity.core.model.keys.Keys
import de.connect2x.trixnity.crypto.driver.sas.EstablishedSas
import de.connect2x.trixnity.crypto.of

private val log = KotlinLogging.logger("de.connect2x.trixnity.client.verification.ActiveSasVerificationState")

sealed interface ActiveSasVerificationState {
    data class OwnSasStart(
        val content: SasStartEventContent
    ) : ActiveSasVerificationState

    data class TheirSasStart(
        val content: SasStartEventContent,
        private val sasPublicKey: KeyValue.Curve25519KeyValue,
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
                            commitment = createSasCommitment(sasPublicKey.value, content, json),
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
        private val establishedSas: EstablishedSas,
        private val keyStore: KeyStore,
        private val send: suspend (stepContent: VerificationStep) -> Unit,
    ) : ActiveSasVerificationState {
        private val actualTransactionId = relatesTo?.eventId?.full
            ?: transactionId
            ?: throw IllegalArgumentException("actualTransactionId should never be null")

        suspend fun match() {
            when (messageAuthenticationCode) {
                HkdfHmacSha256 -> {
                    log.trace { "sendHkdfHmacSha256Mac with old (wrong) base64" }
                    sendHkdfHmacSha256Mac { input, info ->
                        establishedSas.calculateMacInvalidBase64(input, info).let(::MacValue)
                    }
                }

                HkdfHmacSha256V2 -> {
                    log.trace { "sendHkdfHmacSha256Mac with fixed base64" }
                    sendHkdfHmacSha256Mac { input, info ->
                        establishedSas.calculateMac(input, info).let(MacValue::of)
                    }
                }

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

        private suspend fun sendHkdfHmacSha256Mac(calculateMac: (String, String) -> MacValue) {
            val baseInfo = "MATRIX_KEY_VERIFICATION_MAC" +
                    ownUserId.full + ownDeviceId +
                    theirUserId.full + theirDeviceId +
                    actualTransactionId
            val keysToMac = keyStore.getAllKeysFromUser<Ed25519Key>(ownUserId, ownDeviceId, MasterKey)
            if (keysToMac.isNotEmpty()) {
                val input = keysToMac.map { it.fullId }.sortedBy { it }.joinToString(",")
                val info = baseInfo + "KEY_IDS"
                log.trace { "create keys mac from input $input and info $info" }
                val keys = calculateMac(input, info)
                val macs =
                    keysToMac.map {
                        log.trace { "create key mac from input $it and info ${baseInfo + it.fullId}" }
                        it.copy(
                            value = KeyValue.Ed25519KeyValue(
                                calculateMac(
                                    it.value.value, baseInfo + it.fullId
                                ).value
                            )
                        )
                    }
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

    data object WaitForMacs : ActiveSasVerificationState
}