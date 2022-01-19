package net.folivo.trixnity.client.verification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.folivo.trixnity.client.crypto.getAllKeysFromUser
import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.verification.ActiveSasVerificationState.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.model.events.m.key.verification.*
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.*
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStartEventContent.SasStartEventContent
import net.folivo.trixnity.olm.OlmSAS

private val log = KotlinLogging.logger {}

class ActiveSasVerificationMethod private constructor(
    override val startEventContent: SasStartEventContent,
    private val weStartedVerification: Boolean,
    private val ownUserId: UserId,
    private val ownDeviceId: String,
    private val theirUserId: UserId,
    private val theirDeviceId: String,
    private val relatesTo: VerificationStepRelatesTo?,
    private val transactionId: String?,
    private val sendVerificationStep: suspend (step: VerificationStep) -> Unit,
    private val store: Store,
    private val keyService: KeyService,
    private val json: Json,
) : ActiveVerificationMethod() {

    private val actualTransactionId = relatesTo?.eventId?.full
        ?: transactionId
        ?: throw IllegalArgumentException("actualTransactionId should never be null")

    private val olmSas = OlmSAS.create()

    private val _state: MutableStateFlow<ActiveSasVerificationState> =
        MutableStateFlow(
            if (weStartedVerification) OwnSasStart(startEventContent)
            else TheirSasStart(
                startEventContent,
                olmSas,
                json,
                relatesTo,
                transactionId,
                sendVerificationStep
            )
        )
    val state = _state.asStateFlow()

    private var theirCommitment: String? = null
    private var theirPublicKey: String? = null
    private var theirMac: SasMacEventContent? = null
    private var messageAuthenticationCode: String? = null

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
            relatesTo: VerificationStepRelatesTo?,
            transactionId: String?,
            sendVerificationStep: suspend (step: VerificationStep) -> Unit,
            store: Store,
            keyService: KeyService,
            json: Json,
        ): ActiveSasVerificationMethod? {
            return if (!startEventContent.keyAgreementProtocols.contains("curve25519-hkdf-sha256")) {
                sendVerificationStep(
                    VerificationCancelEventContent(
                        UnknownMethod,
                        "key agreement protocol not supported",
                        relatesTo,
                        transactionId
                    )
                )
                null
            } else return ActiveSasVerificationMethod(
                startEventContent,
                weStartedVerification,
                ownUserId,
                ownDeviceId,
                theirUserId,
                theirDeviceId,
                relatesTo,
                transactionId,
                sendVerificationStep,
                store,
                keyService,
                json,
            )
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
            is VerificationCancelEventContent -> {
                onCancel()
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
        _state.value = Accept(isOurOwn)
        messageAuthenticationCode = stepContent.messageAuthenticationCode
        if (!isOurOwn) {
            if (stepContent.keyAgreementProtocol == "curve25519-hkdf-sha256") {
                theirCommitment = stepContent.commitment
                sendVerificationStep(SasKeyEventContent(olmSas.publicKey, relatesTo, transactionId))
            } else {
                sendVerificationStep(
                    VerificationCancelEventContent(
                        UnknownMethod,
                        "key agreement protocol not supported",
                        relatesTo,
                        transactionId
                    )
                )
            }
        }
    }

    private suspend fun onKey(stepContent: SasKeyEventContent, isOurOwn: Boolean) {
        if (!isOurOwn) theirPublicKey = stepContent.key

        when (val currentState = state.value) {
            is Accept -> {
                _state.value = WaitForKeys(isOurOwn)
                if (currentState.isOurOwn != isOurOwn) {
                    if (!isOurOwn) {
                        sendVerificationStep(SasKeyEventContent(olmSas.publicKey, relatesTo, transactionId))
                    }
                } else cancelUnexpectedMessage(currentState)
            }
            is WaitForKeys -> {
                if (currentState.isOurOwn != isOurOwn) {
                    fun createComparison() {
                        olmSas.setTheirPublicKey(
                            theirPublicKey ?: throw IllegalArgumentException("their public key should never be null")
                        )
                        val ownInfo = "${ownUserId}|${ownDeviceId}|${olmSas.publicKey}|"
                        val theirInfo = "${theirUserId}|${theirDeviceId}|${theirPublicKey}|"
                        val sasInfo = "MATRIX_KEY_VERIFICATION_SAS|" +
                                (if (weStartedVerification) ownInfo + theirInfo else theirInfo + ownInfo) +
                                actualTransactionId
                        val shortCode = olmSas.generateShortCode(sasInfo, 6).map { it.toUByte() }
                        val decimal = listOf(
                            ((shortCode[0].toInt() shl 5) or (shortCode[1].toInt() shr 3)) + 1000,
                            (((shortCode[1].toInt() and 0x7) shl 10) or (shortCode[2].toInt() shl 2) or (shortCode[3].toInt() shr 6)) + 1000,
                            (((shortCode[3].toInt() and 0x3F) shl 7) or (shortCode[4].toInt() shr 1)) + 1000
                        )
                        val emojis = listOf(
                            shortCode[0].toInt() shr 2,
                            ((shortCode[0].toInt() and 0x3) shl 4) or (shortCode[1].toInt() shr 4),
                            ((shortCode[1].toInt() and 0xF) shl 2) or (shortCode[2].toInt() shr 6),
                            shortCode[2].toInt() and 0x3F,
                            shortCode[3].toInt() shr 2,
                            ((shortCode[3].toInt() and 0x3) shl 4) or (shortCode[4].toInt() shr 4),
                            ((shortCode[4].toInt() and 0xF) shl 2) or (shortCode[5].toInt() shr 6),
                        ).map {
                            it to (numberToEmojiMapping[it]
                                ?: throw IllegalStateException("Cannot find emoji for number $it."))
                        }
                        _state.value = ComparisonByUser(
                            decimal = decimal, emojis = emojis,
                            ownUserId = ownUserId, ownDeviceId = ownDeviceId,
                            theirUserId = theirUserId, theirDeviceId = theirDeviceId,
                            messageAuthenticationCode = messageAuthenticationCode
                                ?: throw IllegalArgumentException("should never be null at this step"),
                            relatesTo = relatesTo,
                            transactionId = transactionId,
                            olmSas = olmSas,
                            store = store,
                            send = sendVerificationStep
                        ).also { log.debug { "created comparison: $it" } }
                    }
                    if (!isOurOwn) {
                        if (theirCommitment != null
                            && createSasCommitment(stepContent.key, startEventContent, json) == theirCommitment
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
        if (!isOurOwn) theirMac = stepContent
        val theirMac = theirMac

        when {
            theirMac == null && state.value is ComparisonByUser -> _state.value = WaitForMacs
            theirMac != null && (state.value == WaitForMacs || isOurOwn) -> {
                val baseInfo = "MATRIX_KEY_VERIFICATION_MAC" +
                        theirUserId + theirDeviceId +
                        ownUserId + ownDeviceId +
                        actualTransactionId
                val theirMacs = theirMac.mac.keys.filterIsInstance<Ed25519Key>()
                val theirMacIds = theirMacs.mapNotNull { it.fullKeyId }
                val allKeysOfDevice = store.keys.getAllKeysFromUser<Ed25519Key>(theirUserId, theirDeviceId)
                val keysToMac = allKeysOfDevice.filter { theirMacIds.contains(it.fullKeyId) }
                val input = theirMacIds.sortedBy { it }.joinToString(",")
                val info = baseInfo + "KEY_IDS"
                log.debug { "create keys mac from input $input and info $info" }
                val keys = olmSas.calculateMac(input, info)
                if (keys == theirMac.keys) {
                    val containsMismatchedMac = keysToMac.asSequence()
                        .map { keyToMac ->
                            log.debug { "create key mac from input ${keyToMac.value} and info ${baseInfo + keyToMac.fullKeyId}" }
                            val calculatedMac =
                                olmSas.calculateMac(keyToMac.value, baseInfo + keyToMac.fullKeyId)
                            (calculatedMac == theirMac.mac.find { it.fullKeyId == keyToMac.fullKeyId }?.value).also {
                                if (!it) log.warn { "macs from them (${keyToMac}) did not match our calculated ($calculatedMac)" }
                            }
                        }.contains(false)
                    if (!containsMismatchedMac) {
                        keyService.trustAndSignKeys(keysToMac.toSet(), theirUserId)
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
            else -> {}
        }
    }

    private fun onCancel() {
        olmSas.free()
    }

}