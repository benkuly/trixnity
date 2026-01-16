package de.connect2x.trixnity.client.verification

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import de.connect2x.trixnity.client.key.KeyTrustService
import de.connect2x.trixnity.client.key.getAllKeysFromUser
import de.connect2x.trixnity.client.store.KeyStore
import de.connect2x.trixnity.client.verification.ActiveSasVerificationState.*
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.RelatesTo
import de.connect2x.trixnity.core.model.events.m.key.verification.*
import de.connect2x.trixnity.core.model.events.m.key.verification.SasKeyAgreementProtocol.Curve25519HkdfSha256
import de.connect2x.trixnity.core.model.events.m.key.verification.SasMessageAuthenticationCode.HkdfHmacSha256
import de.connect2x.trixnity.core.model.events.m.key.verification.SasMessageAuthenticationCode.HkdfHmacSha256V2
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.*
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationStartEventContent.SasStartEventContent
import de.connect2x.trixnity.core.model.keys.Key.Ed25519Key
import de.connect2x.trixnity.core.model.keys.KeyValue
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.crypto.driver.CryptoDriver
import de.connect2x.trixnity.crypto.driver.sas.EstablishedSas
import de.connect2x.trixnity.crypto.driver.sas.Sas
import de.connect2x.trixnity.crypto.driver.useAll
import de.connect2x.trixnity.crypto.of

private val log = KotlinLogging.logger("de.connect2x.trixnity.client.verification.ActiveSasVerificationMethod")

class ActiveSasVerificationMethod private constructor(
    override val startEventContent: SasStartEventContent,
    private val weStartedVerification: Boolean,
    private val ownUserId: UserId,
    private val ownDeviceId: String,
    private val theirUserId: UserId,
    private val theirDeviceId: String,
    private val relatesTo: RelatesTo.Reference?,
    private val transactionId: String?,
    private val sendVerificationStep: suspend (step: VerificationStep) -> Unit,
    private val keyStore: KeyStore,
    private val keyTrustService: KeyTrustService,
    private val json: Json,
    private val driver: CryptoDriver,
    private val olmSas: Sas,
) : ActiveVerificationMethod() {

    private val actualTransactionId = relatesTo?.eventId?.full
        ?: transactionId
        ?: throw IllegalArgumentException("actualTransactionId should never be null")

    private val _state: MutableStateFlow<ActiveSasVerificationState> =
        MutableStateFlow(
            if (weStartedVerification) OwnSasStart(startEventContent)
            else TheirSasStart(
                startEventContent,
                KeyValue.of(olmSas.publicKey),
                json,
                relatesTo,
                transactionId,
                sendVerificationStep
            )
        )
    val state = _state.asStateFlow()

    private var theirCommitment: String? = null
    private var theirPublicKey: Curve25519KeyValue? = null
    private var theirMac: SasMacEventContent? = null
    private var ourMac: SasMacEventContent? = null
    private var establishedSas: EstablishedSas? = null
    private var messageAuthenticationCode: SasMessageAuthenticationCode? = null

    companion object {
        val numberToEmojiMapping = mapOf(
            0 to "🐶", 1 to "🐱", 2 to "🦁", 3 to "🐎", 4 to "🦄", 5 to "🐷", 6 to "🐘", 7 to "🐰", 8 to "🐼",
            9 to "🐓", 10 to "🐧", 11 to "🐢", 12 to "🐟", 13 to "🐙", 14 to "🦋", 15 to "🌷", 16 to "🌳", 17 to "🌵",
            18 to "🍄", 19 to "🌏", 20 to "🌙", 21 to "☁", 22 to "🔥", 23 to "🍌", 24 to "🍎", 25 to "🍓", 26 to "🌽",
            27 to "🍕", 28 to "🎂", 29 to "❤", 30 to "😀", 31 to "🤖", 32 to "🎩", 33 to "👓", 34 to "🔧", 35 to "🎅",
            36 to "👍", 37 to "☂", 38 to "⌛", 39 to "⏰", 40 to "🎁", 41 to "💡", 42 to "📕", 43 to "✏", 44 to "📎",
            45 to "✂", 46 to "🔒", 47 to "🔑", 48 to "🔨", 49 to "☎", 50 to "🏁", 51 to "🚂", 52 to "🚲", 53 to "✈",
            54 to "🚀", 55 to "🏆", 56 to "⚽", 57 to "🎸", 58 to "🎺", 59 to "🔔", 60 to "⚓", 61 to "🎧", 62 to "📁",
            63 to "📌"
        )

        suspend fun create(
            startEventContent: SasStartEventContent,
            weStartedVerification: Boolean,
            ownUserId: UserId,
            ownDeviceId: String,
            theirUserId: UserId,
            theirDeviceId: String,
            relatesTo: RelatesTo.Reference?,
            transactionId: String?,
            sendVerificationStep: suspend (step: VerificationStep) -> Unit,
            keyStore: KeyStore,
            keyTrustService: KeyTrustService,
            json: Json,
            driver: CryptoDriver,
        ): ActiveSasVerificationMethod? {
            return when {
                startEventContent.keyAgreementProtocols.none { it == Curve25519HkdfSha256 } -> {
                    sendVerificationStep(
                        VerificationCancelEventContent(
                            UnknownMethod,
                            "key agreement protocol not supported",
                            relatesTo,
                            transactionId
                        )
                    )
                    null
                }

                startEventContent.shortAuthenticationString.none { it == SasMethod.Emoji || it == SasMethod.Decimal } -> {
                    sendVerificationStep(
                        VerificationCancelEventContent(
                            UnknownMethod,
                            "short authentication string not supported",
                            relatesTo,
                            transactionId
                        )
                    )
                    null
                }

                else -> ActiveSasVerificationMethod(
                    startEventContent,
                    weStartedVerification,
                    ownUserId,
                    ownDeviceId,
                    theirUserId,
                    theirDeviceId,
                    relatesTo,
                    transactionId,
                    sendVerificationStep,
                    keyStore,
                    keyTrustService,
                    json,
                    driver,
                    driver.sas(),
                )
            }
        }
    }

    override suspend fun handleVerificationStep(step: VerificationStep, isOurOwn: Boolean) {
        val currentState = state.value
        when (step) {
            is SasAcceptEventContent -> {
                if (currentState is OwnSasStart || currentState is TheirSasStart)
                    onAccept(step, isOurOwn)
                else cancelUnexpectedMessage(currentState)
            }

            is SasKeyEventContent -> {
                if (currentState is Accept || currentState is WaitForKeys)
                    onKey(step, isOurOwn)
                else cancelUnexpectedMessage(currentState)
            }

            is SasMacEventContent -> {
                if (currentState is ComparisonByUser || currentState is WaitForMacs)
                    onMac(step, isOurOwn)
                else cancelUnexpectedMessage(currentState)
            }

            is VerificationCancelEventContent, is VerificationDoneEventContent -> {
                onDoneOrCancel()
            }
        }
    }

    private suspend fun cancelUnexpectedMessage(currentState: ActiveSasVerificationState) {
        sendVerificationStep(
            VerificationCancelEventContent(
                UnexpectedMessage,
                "this verification is at SAS step ${currentState::class.simpleName}",
                relatesTo,
                transactionId
            )
        )
    }

    private suspend fun onAccept(stepContent: SasAcceptEventContent, isOurOwn: Boolean) {
        log.trace { "onAccept $stepContent (isOurOwn=$isOurOwn)" }
        _state.value = Accept(isOurOwn)
        messageAuthenticationCode = stepContent.messageAuthenticationCode
        if (!isOurOwn) {
            when {
                stepContent.hash != SasHash.Sha256 ->
                    sendVerificationStep(
                        VerificationCancelEventContent(
                            UnknownMethod,
                            "only hashes [${SasHash.Sha256.name}] are supported",
                            relatesTo,
                            transactionId
                        )
                    )

                stepContent.keyAgreementProtocol != Curve25519HkdfSha256 ->
                    sendVerificationStep(
                        VerificationCancelEventContent(
                            UnknownMethod,
                            "only key agreement protocols [${Curve25519HkdfSha256.name}] are supported",
                            relatesTo,
                            transactionId
                        )
                    )

                stepContent.messageAuthenticationCode != HkdfHmacSha256
                        && stepContent.messageAuthenticationCode != HkdfHmacSha256V2 ->
                    sendVerificationStep(
                        VerificationCancelEventContent(
                            UnknownMethod,
                            "only message authentication codes [${HkdfHmacSha256.name} ${HkdfHmacSha256V2.name}] are supported",
                            relatesTo,
                            transactionId
                        )
                    )

                stepContent.shortAuthenticationString.contains(SasMethod.Decimal).not()
                        && stepContent.shortAuthenticationString.contains(SasMethod.Emoji).not() ->
                    sendVerificationStep(
                        VerificationCancelEventContent(
                            UnknownMethod,
                            "only short authentication strings [${SasMethod.Decimal.name} ${SasMethod.Emoji.name}] are supported",
                            relatesTo,
                            transactionId
                        )
                    )

                else -> {
                    theirCommitment = stepContent.commitment
                    sendVerificationStep(
                        SasKeyEventContent(
                            KeyValue.of(olmSas.publicKey),
                            relatesTo,
                            transactionId
                        )
                    )
                }
            }
        }
    }

    private suspend fun onKey(stepContent: SasKeyEventContent, isOurOwn: Boolean) {
        log.trace { "onKey $stepContent (isOurOwn=$isOurOwn)" }

        if (!isOurOwn) theirPublicKey = stepContent.key

        when (val currentState = state.value) {
            is Accept -> {
                _state.value = WaitForKeys(isOurOwn)
                if (currentState.isOurOwn != isOurOwn) {
                    if (!isOurOwn) {
                        sendVerificationStep(
                            SasKeyEventContent(
                                KeyValue.of(olmSas.publicKey),
                                relatesTo,
                                transactionId
                            )
                        )
                    }
                } else cancelUnexpectedMessage(currentState)
            }

            is WaitForKeys -> {
                if (currentState.isOurOwn != isOurOwn) {
                    fun createComparison() {
                        val theirPublicKey = requireNotNull(theirPublicKey) {
                            "their public key should never be null"
                        }.value

                        val ownInfo = "${ownUserId.full}|${ownDeviceId}|${olmSas.publicKey.base64}|"
                        val theirInfo = "${theirUserId.full}|${theirDeviceId}|${theirPublicKey}|"
                        val sasInfo = "MATRIX_KEY_VERIFICATION_SAS|" +
                                (if (weStartedVerification) ownInfo + theirInfo else theirInfo + ownInfo) +
                                actualTransactionId
                        log.trace { "generate short code from sas info: $sasInfo" }

                        val established = useAll(
                            { olmSas },
                            { driver.key.curve25519PublicKey(theirPublicKey) }
                        ) { sas, theirPublicKey -> sas.diffieHellman(theirPublicKey) }

                        establishedSas = established

                        val (decimal, rawEmojis) = established.generateBytes(sasInfo).use {
                            it.decimals.asList to it.emojiIndices.asList
                        }
                        val emojis = rawEmojis.asSequence().map {
                            it to checkNotNull(numberToEmojiMapping[it]) { "Cannot find emoji for number $it." }
                        }.toList()
                        _state.value = ComparisonByUser(
                            decimal = decimal, emojis = emojis,
                            ownUserId = ownUserId, ownDeviceId = ownDeviceId,
                            theirUserId = theirUserId, theirDeviceId = theirDeviceId,
                            messageAuthenticationCode = messageAuthenticationCode
                                ?: throw IllegalArgumentException("should never be null at this step"),
                            relatesTo = relatesTo,
                            transactionId = transactionId,
                            establishedSas = checkNotNull(establishedSas) { "should never be null at this step" },
                            keyStore = keyStore,
                            send = sendVerificationStep
                        ).also { log.debug { "created comparison: $it" } }
                    }
                    if (!isOurOwn) {
                        if (theirCommitment != null
                            && createSasCommitment(stepContent.key.value, startEventContent, json) == theirCommitment
                        ) {
                            theirPublicKey = stepContent.key
                            createComparison()
                        } else sendVerificationStep(
                            VerificationCancelEventContent(
                                MismatchedCommitment,
                                "mismatched commitment",
                                relatesTo,
                                transactionId
                            )
                        )
                    } else createComparison()

                } else cancelUnexpectedMessage(currentState)
            }

            else -> {}
        }
    }

    private suspend fun onMac(stepContent: SasMacEventContent, isOurOwn: Boolean) {
        log.trace { "onMac $stepContent (isOurOwn=$isOurOwn, hasOwnMac=${ourMac != null}, hasTheirMac = ${theirMac != null})" }

        if (isOurOwn) {
            ourMac = stepContent
        } else {
            theirMac = stepContent
        }
        val ourMac = ourMac
        val theirMac = theirMac

        if (ourMac != null && theirMac != null) {
            when (messageAuthenticationCode) {
                HkdfHmacSha256 -> {
                    log.trace { "checkHkdfHmacSha256Mac with old (wrong) base64" }
                    checkHkdfHmacSha256Mac(theirMac) { input, info ->
                        checkNotNull(establishedSas).calculateMacInvalidBase64(input, info)
                    }
                }

                HkdfHmacSha256V2 -> {
                    log.trace { "checkHkdfHmacSha256Mac with fixed base64" }
                    checkHkdfHmacSha256Mac(theirMac) { input, info ->
                        checkNotNull(establishedSas).calculateMac(input, info).base64
                    }
                }

                else -> {
                    log.warn { "messageAuthenticationCode is not set" }
                    sendVerificationStep(
                        VerificationCancelEventContent(
                            UnexpectedMessage,
                            "messageAuthenticationCode is not set",
                            relatesTo,
                            transactionId
                        )
                    )
                }
            }
        } else if (isOurOwn) {
            _state.value = WaitForMacs
        }
    }

    private suspend fun checkHkdfHmacSha256Mac(theirMac: SasMacEventContent, calculateMac: (String, String) -> String) {
        val baseInfo = "MATRIX_KEY_VERIFICATION_MAC" +
                theirUserId.full + theirDeviceId +
                ownUserId.full + ownDeviceId +
                actualTransactionId
        val theirMacs = theirMac.mac.keys.filterIsInstance<Ed25519Key>()
        val theirMacIds = theirMacs.map { it.fullId }
        val input = theirMacIds.sortedBy { it }.joinToString(",")
        val info = baseInfo + "KEY_IDS"
        log.trace { "create keys mac from input $input and info $info" }
        val keys = calculateMac(input, info)
        if (keys == theirMac.keys.value) {
            val allKeysOfDevice = keyStore.getAllKeysFromUser<Ed25519Key>(theirUserId, theirDeviceId)
            val keysToMac = allKeysOfDevice.filter { theirMacIds.contains(it.fullId) }
            val containsMismatchedMac = keysToMac.asSequence()
                .map { keyToMac ->
                    log.trace { "create key mac from input ${keyToMac.value} and info ${baseInfo + keyToMac.fullId}" }
                    val calculatedMac =
                        calculateMac(keyToMac.value.value, baseInfo + keyToMac.fullId)
                    (calculatedMac == theirMac.mac.find { it.fullId == keyToMac.fullId }?.value?.value).also {
                        if (!it) log.warn { "macs from them (${keyToMac}) did not match our calculated ($calculatedMac)" }
                    }
                }.contains(false)
            if (!containsMismatchedMac) {
                keyTrustService.trustAndSignKeys(keysToMac.toSet(), theirUserId)
                sendVerificationStep(VerificationDoneEventContent(relatesTo, transactionId))
            } else {
                sendVerificationStep(
                    VerificationCancelEventContent(KeyMismatch, "macs did not match", relatesTo, transactionId)
                )
            }
        } else {
            log.warn { "keys from them (${theirMac.keys}) did not match our calculated ($keys)" }
            sendVerificationStep(
                VerificationCancelEventContent(KeyMismatch, "keys mac did not match", relatesTo, transactionId)
            )
        }
    }

    private fun onDoneOrCancel() {
        olmSas.close()
        establishedSas?.close()
    }

}