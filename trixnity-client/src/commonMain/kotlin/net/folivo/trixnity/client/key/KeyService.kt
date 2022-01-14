package net.folivo.trixnity.client.key

import com.benasher44.uuid.uuid4
import io.ktor.util.*
import io.ktor.util.reflect.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import mu.KotlinLogging
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.UIA
import net.folivo.trixnity.client.api.injectOnSuccessIntoUIA
import net.folivo.trixnity.client.api.model.sync.SyncResponse
import net.folivo.trixnity.client.crypto.*
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.*
import net.folivo.trixnity.client.crypto.OlmSignService.SignWith
import net.folivo.trixnity.client.retryWhenSyncIsRunning
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.AllowedSecretType.M_CROSS_SIGNING_SELF_SIGNING
import net.folivo.trixnity.client.store.AllowedSecretType.M_CROSS_SIGNING_USER_SIGNING
import net.folivo.trixnity.client.verification.KeyVerificationState
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.*
import net.folivo.trixnity.core.model.crypto.CrossSigningKeysUsage.*
import net.folivo.trixnity.core.model.crypto.Key.Ed25519Key
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.KeyRequestAction
import net.folivo.trixnity.core.model.events.m.crosssigning.MasterKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.secret.SecretKeyRequestEventContent
import net.folivo.trixnity.core.model.events.m.secret.SecretKeySendEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key.AesHmacSha2EncryptedData
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.SecretStorageKeyPassphrase.Pbkdf2
import net.folivo.trixnity.olm.OlmPkSigning
import net.folivo.trixnity.olm.decodeUnpaddedBase64Bytes
import net.folivo.trixnity.olm.freeAfter
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.flatMap
import kotlin.collections.toSet
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import arrow.core.flatMap as flatMapResult

private val log = KotlinLogging.logger {}

class KeyService(
    private val store: Store,
    private val olm: OlmService,
    private val api: MatrixApiClient,
) {
    private val ownUserId = store.account.userId.value ?: throw IllegalArgumentException("userId must not be null")
    private val ownDeviceId =
        store.account.deviceId.value ?: throw IllegalArgumentException("deviceId must not be null")

    @OptIn(FlowPreview::class)
    internal suspend fun start(scope: CoroutineScope) {
        api.sync.subscribeSyncResponse { syncResponse ->
            syncResponse.deviceLists?.also { handleDeviceLists(it) }
        }
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        scope.launch(start = CoroutineStart.UNDISPATCHED) { handleOutdatedKeys() }
        scope.launch(start = CoroutineStart.UNDISPATCHED) { olm.decryptedOlmEvents.collect(::handleEncryptedIncomingKeyRequests) }
        scope.launch(start = CoroutineStart.UNDISPATCHED) { olm.decryptedOlmEvents.collect(::handleOutgoingKeyRequestAnswer) }
        scope.launch(start = CoroutineStart.UNDISPATCHED) { requestSecretKeysWhenCrossSigned() }
        api.sync.subscribeAfterSyncResponse {
            processIncomingKeyRequests()
            cancelOldOutgoingKeyRequests()
        }
        api.sync.subscribe(::handleChangedSecrets)
        api.sync.subscribe(::handleIncomingKeyRequests)
    }

    internal suspend fun handleDeviceLists(deviceList: SyncResponse.DeviceLists) {
        log.debug { "set outdated device keys or remove old device keys" }
        deviceList.changed?.let { userIds ->
            store.keys.outdatedKeys.update { oldUserIds ->
                oldUserIds + userIds.filter { store.keys.isTracked(it) }
            }
        }
        deviceList.left?.forEach { userId ->
            store.keys.outdatedKeys.update { it - userId }
            store.keys.updateDeviceKeys(userId) { null }
            store.keys.updateCrossSigningKeys(userId) { null }
        }
    }

    @OptIn(FlowPreview::class)
    internal suspend fun handleOutdatedKeys() = coroutineScope {
        api.sync.currentSyncState.retryWhenSyncIsRunning(
            onError = { log.warn(it) { "failed update outdated keys" } },
            onCancel = { log.info { "stop update outdated keys, because job was cancelled" } },
            scope = this
        ) {
            store.keys.outdatedKeys.debounce(100).collectLatest { userIds ->
                if (userIds.isNotEmpty()) {
                    log.debug { "try update outdated keys of $userIds" }
                    val keysResponse = api.keys.getKeys(
                        deviceKeys = userIds.associateWith { emptySet() },
                        token = store.account.syncBatchToken.value
                    ).getOrThrow()

                    keysResponse.masterKeys?.forEach { (userId, masterKey) ->
                        handleOutdatedCrossSigningKey(userId, masterKey, MasterKey, masterKey.getSelfSigningKey(), true)
                    }
                    keysResponse.selfSigningKeys?.forEach { (userId, selfSigningKey) ->
                        handleOutdatedCrossSigningKey(
                            userId, selfSigningKey, SelfSigningKey,
                            store.keys.getCrossSigningKey(userId, MasterKey)?.value?.signed?.get()
                        )
                    }
                    keysResponse.userSigningKeys?.forEach { (userId, userSigningKey) ->
                        handleOutdatedCrossSigningKey(
                            userId, userSigningKey, UserSigningKey,
                            store.keys.getCrossSigningKey(userId, MasterKey)?.value?.signed?.get()
                        )
                    }
                    val joinedEncryptedRooms = async(start = CoroutineStart.LAZY) { store.room.encryptedJoinedRooms() }
                    keysResponse.deviceKeys?.forEach { (userId, devices) ->
                        handleOutdatedDeviceKeys(userId, devices, joinedEncryptedRooms)
                    }
                    joinedEncryptedRooms.cancel()
                    store.keys.outdatedKeys.update { it - userIds }
                }
            }
        }
    }

    private suspend fun handleOutdatedCrossSigningKey(
        userId: UserId,
        crossSigningKey: Signed<CrossSigningKeys, UserId>,
        usage: CrossSigningKeysUsage,
        signingKeyForVerification: Ed25519Key?,
        signingOptional: Boolean = false
    ) {
        log.debug { "update outdated master key for user $userId" }
        val signatureVerification =
            olm.sign.verify(crossSigningKey, mapOf(userId to setOfNotNull(signingKeyForVerification)))
        if (signatureVerification == VerifyResult.Valid
            || signingOptional && signatureVerification is VerifyResult.MissingSignature
        ) {
            val newKey = StoredCrossSigningKeys(crossSigningKey, calculateCrossSigningKeysTrustLevel(crossSigningKey))
            store.keys.updateCrossSigningKeys(userId) { oldKeys ->
                ((oldKeys?.filterNot { it.value.signed.usage.contains(usage) }
                    ?.toSet() ?: setOf())
                        + newKey)
            }
        } else {
            log.warn { "Signatures from cross signing key (${usage.name}) of $userId were not valid: $signatureVerification!" }
        }
    }

    private suspend fun handleOutdatedDeviceKeys(
        userId: UserId,
        devices: Map<String, Signed<DeviceKeys, UserId>>,
        joinedEncryptedRooms: Deferred<List<RoomId>>
    ) {
        log.debug { "update outdated device keys for user $userId" }
        val oldDevices = store.keys.getDeviceKeys(userId)
        val newDevices = devices.filter { (deviceId, deviceKeys) ->
            val signatureVerification =
                olm.sign.verify(deviceKeys, mapOf(userId to setOfNotNull(deviceKeys.getSelfSigningKey())))
            (userId == deviceKeys.signed.userId && deviceId == deviceKeys.signed.deviceId
                    && signatureVerification == VerifyResult.Valid)
                .also {
                    if (!it) log.warn { "Signatures from device key $deviceId of $userId were not valid: $signatureVerification!" }
                }
        }.mapValues { (_, deviceKeys) ->
            StoredDeviceKeys(deviceKeys, calculateDeviceKeysTrustLevel(deviceKeys))
        }
        val addedDeviceKeys = if (oldDevices != null) newDevices.keys - oldDevices.keys else newDevices.keys
        if (addedDeviceKeys.isNotEmpty()) {
            log.debug { "look for encrypted room, where the user participates and notify megolm sessions about new device keys from $userId: $addedDeviceKeys" }
            joinedEncryptedRooms.await()
                .filter { roomId ->
                    store.roomState.getByStateKey<MemberEventContent>(roomId, userId.full)
                        ?.content?.membership.let { it == MemberEventContent.Membership.JOIN || it == MemberEventContent.Membership.INVITE }
                }.forEach { roomId ->
                    store.olm.updateOutboundMegolmSession(roomId) { oms ->
                        oms?.copy(
                            newDevices = oms.newDevices + Pair(
                                userId,
                                oms.newDevices[userId]?.plus(addedDeviceKeys) ?: addedDeviceKeys
                            )
                        )
                    }
                }
        }
        store.keys.updateCrossSigningKeys(userId) { oldKeys ->
            val usersMasterKey = oldKeys?.find { it.value.signed.usage.contains(MasterKey) }
            if (usersMasterKey != null) {
                val notFullyCrossSigned = newDevices.any { it.value.trustLevel == NotCrossSigned }
                val oldMasterKeyTrustLevel = usersMasterKey.trustLevel
                val newMasterKeyTrustLevel = when (oldMasterKeyTrustLevel) {
                    is CrossSigned -> {
                        if (notFullyCrossSigned) {
                            log.debug { "mark master key of $userId as ${NotAllDeviceKeysCrossSigned::class.simpleName}" }
                            NotAllDeviceKeysCrossSigned(oldMasterKeyTrustLevel.verified)
                        } else oldMasterKeyTrustLevel
                    }
                    else -> oldMasterKeyTrustLevel
                }
                if (oldMasterKeyTrustLevel != newMasterKeyTrustLevel) {
                    (oldKeys - usersMasterKey) + usersMasterKey.copy(trustLevel = newMasterKeyTrustLevel)
                } else oldKeys
            } else oldKeys
        }
        store.keys.updateDeviceKeys(userId) { newDevices }
    }

    internal suspend fun updateTrustLevel(signingUserId: UserId, signingKey: Ed25519Key) {
        updateTrustLevelOfKey(signingUserId, signingKey)
        store.keys.getKeyChainLinksBySigningKey(signingUserId, signingKey).forEach { keyChainLink ->
            updateTrustLevelOfKey(keyChainLink.signedUserId, keyChainLink.signedKey)
        }
    }

    private suspend fun updateTrustLevelOfKey(userId: UserId, key: Ed25519Key) {
        val keyId = key.keyId
        log.debug { "update trust level for $userId key $key" }

        if (keyId != null) {
            val foundKey = MutableStateFlow(false)

            store.keys.updateDeviceKeys(userId) { oldDeviceKeys ->
                val foundDeviceKeys = oldDeviceKeys?.get(keyId)
                if (foundDeviceKeys != null) {
                    val newTrustLevel = calculateDeviceKeysTrustLevel(foundDeviceKeys.value)
                    foundKey.value = true
                    oldDeviceKeys + (keyId to foundDeviceKeys.copy(trustLevel = newTrustLevel))
                } else oldDeviceKeys
            }
            if (foundKey.value.not()) {
                store.keys.updateCrossSigningKeys(userId) { oldKeys ->
                    val foundCrossSigningKeys = oldKeys?.firstOrNull { keys ->
                        keys.value.signed.keys.keys.filterIsInstance<Ed25519Key>().any { it.keyId == keyId }
                    }
                    if (foundCrossSigningKeys != null) {
                        val newTrustLevel = calculateCrossSigningKeysTrustLevel(foundCrossSigningKeys.value)
                        foundKey.value = true
                        (oldKeys - foundCrossSigningKeys) + foundCrossSigningKeys.copy(trustLevel = newTrustLevel)

                    } else oldKeys
                }
            }
            if (foundKey.value.not()) log.warn { "could not find device or cross signing keys of $key" }
        } else log.warn { "could not update trust level, because key id of $key was null" }
    }

    internal suspend fun calculateDeviceKeysTrustLevel(deviceKeys: SignedDeviceKeys): KeySignatureTrustLevel {
        log.trace { "calculate trust level for ${deviceKeys.signed}" }
        val userId = deviceKeys.signed.userId
        val deviceId = deviceKeys.signed.deviceId
        val signedKey = deviceKeys.signed.keys.get<Ed25519Key>()
            ?: return Invalid("missing ed25519 key")
        return calculateTrustLevel(
            userId,
            { olm.sign.verify(deviceKeys, it) },
            signedKey,
            deviceKeys.signatures,
            deviceKeys.getVerificationState(userId, deviceId),
            false
        ).also { log.trace { "calculated trust level of ${deviceKeys.signed} from $userId is $it" } }
    }

    internal suspend fun calculateCrossSigningKeysTrustLevel(crossSigningKeys: SignedCrossSigningKeys): KeySignatureTrustLevel {
        log.trace { "calculate trust level for ${crossSigningKeys.signed}" }
        val userId = crossSigningKeys.signed.userId
        val signedKey = crossSigningKeys.signed.keys.get<Ed25519Key>()
            ?: return Invalid("missing ed25519 key")
        return calculateTrustLevel(
            userId,
            { olm.sign.verify(crossSigningKeys, it) },
            signedKey,
            crossSigningKeys.signatures,
            crossSigningKeys.getVerificationState(userId),
            crossSigningKeys.signed.usage.contains(MasterKey)
        ).also { log.trace { "calculated trust level of ${crossSigningKeys.signed} from $userId is $it" } }
    }

    private suspend fun calculateTrustLevel(
        userId: UserId,
        verifySignedObject: suspend (signingKeys: Map<UserId, Set<Ed25519Key>>) -> VerifyResult,
        signedKey: Ed25519Key,
        signatures: Signatures<UserId>,
        keyVerificationState: KeyVerificationState?,
        isMasterKey: Boolean
    ): KeySignatureTrustLevel {
        val masterKey = store.keys.getCrossSigningKey(userId, MasterKey)
        return when {
            keyVerificationState is KeyVerificationState.Verified && isMasterKey -> CrossSigned(true)
            keyVerificationState is KeyVerificationState.Verified && (masterKey == null) -> Valid(true)
            keyVerificationState is KeyVerificationState.Blocked -> Blocked
            else -> searchSignaturesForTrustLevel(userId, verifySignedObject, signedKey, signatures)
                ?: when {
                    isMasterKey -> CrossSigned(false)
                    else -> if (masterKey == null) Valid(false) else NotCrossSigned
                }
        }
    }

    private suspend fun searchSignaturesForTrustLevel(
        signedUserId: UserId,
        verifySignedObject: suspend (signingKeys: Map<UserId, Set<Ed25519Key>>) -> VerifyResult,
        signedKey: Ed25519Key,
        signatures: Signatures<UserId>,
        visitedKeys: MutableSet<Pair<UserId, String?>> = mutableSetOf()
    ): KeySignatureTrustLevel? {
        log.trace { "search in signatures of $signedKey for trust level calculation: $signatures" }
        visitedKeys.add(signedUserId to signedKey.keyId)
        store.keys.deleteKeyChainLinksBySignedKey(signedUserId, signedKey)
        val states = signatures.flatMap { (signingUserId, signatureKeys) ->
            signatureKeys
                .filterIsInstance<Ed25519Key>()
                .filterNot { visitedKeys.contains(signingUserId to it.keyId) }
                .flatMap { signatureKey ->
                    visitedKeys.add(signingUserId to signatureKey.keyId)

                    val crossSigningKey =
                        signatureKey.keyId?.let { store.keys.getCrossSigningKey(signingUserId, it) }?.value
                    val signingCrossSigningKey = crossSigningKey?.signed?.get<Ed25519Key>()
                    val crossSigningKeyState = if (signingCrossSigningKey != null) {
                        val isValid = verifySignedObject(mapOf(signingUserId to setOf(signingCrossSigningKey)))
                            .also { v ->
                                if (v != VerifyResult.Valid)
                                    log.warn { "signature was $v for key chain $signingCrossSigningKey ($signingUserId) ---> $signedKey ($signedUserId)" }
                            } == VerifyResult.Valid
                        if (isValid) when (crossSigningKey.getVerificationState(signingUserId)) {
                            is KeyVerificationState.Verified -> CrossSigned(true)
                            is KeyVerificationState.Blocked -> Blocked
                            else -> {
                                searchSignaturesForTrustLevel(
                                    signingUserId,
                                    { olm.sign.verify(crossSigningKey, it) },
                                    signingCrossSigningKey,
                                    crossSigningKey.signatures,
                                    visitedKeys
                                ) ?: if (crossSigningKey.signed.usage.contains(MasterKey)
                                    && crossSigningKey.signed.userId == signedUserId
                                    && crossSigningKey.signed.userId == signingUserId
                                ) CrossSigned(false) else null
                            }
                        } else null
                    } else null

                    val deviceKey = signatureKey.keyId?.let { store.keys.getDeviceKey(signingUserId, it) }?.value
                    val signingDeviceKey = deviceKey?.get<Ed25519Key>()
                    val deviceKeyState = if (signingDeviceKey != null) {
                        val isValid = verifySignedObject(mapOf(signingUserId to setOf(signingDeviceKey)))
                            .also { v ->
                                if (v != VerifyResult.Valid)
                                    log.warn { "signature was $v for key chain $signingCrossSigningKey ($signingUserId) ---> $signedKey ($signedUserId)" }
                            } == VerifyResult.Valid
                        if (isValid) when (deviceKey.getVerificationState(signingUserId, deviceKey.signed.deviceId)) {
                            is KeyVerificationState.Verified -> CrossSigned(true)
                            is KeyVerificationState.Blocked -> Blocked
                            else -> searchSignaturesForTrustLevel(
                                signedUserId,
                                { olm.sign.verify(deviceKey, it) },
                                signingDeviceKey,
                                deviceKey.signatures,
                                visitedKeys
                            )
                        } else null
                    } else null

                    val signingKey = signingCrossSigningKey ?: signingDeviceKey
                    if (signingKey != null) {
                        store.keys.saveKeyChainLink(KeyChainLink(signingUserId, signingKey, signedUserId, signedKey))
                    }

                    listOf(crossSigningKeyState, deviceKeyState)
                }.toSet()
        }.toSet()
        return when {
            states.any { it is CrossSigned && it.verified } -> CrossSigned(true)
            states.any { it is CrossSigned && !it.verified } -> CrossSigned(false)
            states.contains(Blocked) -> Blocked
            else -> null
        }
    }

    internal suspend fun trustAndSignKeys(keys: Set<Ed25519Key>, userId: UserId) {
        log.debug { "sign keys (when possible): $keys" }
        val signedDeviceKeys = keys.mapNotNull { key ->
            val deviceKey = key.keyId?.let { store.keys.getDeviceKey(userId, it) }?.value?.signed
            if (deviceKey != null) {
                store.keys.saveKeyVerificationState(
                    key, deviceKey.userId, deviceKey.deviceId, KeyVerificationState.Verified(key.value)
                )
                updateTrustLevel(userId, key)
                try {
                    if (userId == ownUserId && deviceKey.get<Ed25519Key>() == key) {
                        log.info { "sign own accounts device with own self signing key" }
                        olm.sign.sign(deviceKey, SignWith.AllowedSecrets(M_CROSS_SIGNING_SELF_SIGNING))
                    } else null
                } catch (error: Throwable) {
                    log.warn { "could not sign key $key: ${error.message}" }
                    null
                }
            } else null
        }
        val signedCrossSigningKeys = keys.mapNotNull { key ->
            val crossSigningKey = key.keyId?.let { store.keys.getCrossSigningKey(userId, it) }?.value?.signed
            if (crossSigningKey != null) {
                store.keys.saveKeyVerificationState(
                    key, crossSigningKey.userId, null, KeyVerificationState.Verified(key.value)
                )
                updateTrustLevel(userId, key)
                if (crossSigningKey.usage.contains(MasterKey)) {
                    try {
                        if (crossSigningKey.get<Ed25519Key>() == key) {
                            if (userId == ownUserId) {
                                log.info { "sign own master key with own device key" }
                                olm.sign.sign(crossSigningKey, SignWith.DeviceKey)
                            } else {
                                log.info { "sign other users master key with own user signing key" }
                                olm.sign.sign(crossSigningKey, SignWith.AllowedSecrets(M_CROSS_SIGNING_USER_SIGNING))
                            }
                        } else null
                    } catch (error: Throwable) {
                        log.warn { "could not sign key $key: ${error.message}" }
                        null
                    }
                } else null
            } else null
        }
        if (signedDeviceKeys.isNotEmpty() || signedCrossSigningKeys.isNotEmpty()) {
            log.debug { "upload signed keys: ${signedDeviceKeys + signedCrossSigningKeys}" }
            val response = api.keys.addSignatures(signedDeviceKeys.toSet(), signedCrossSigningKeys.toSet()).getOrThrow()
            if (response.failures.isNotEmpty()) {
                log.error { "could not add signatures to server: ${response.failures}" }
                throw UploadSignaturesException(response.failures.toString())
            }
        }
    }

    private suspend fun Keys.getVerificationState(userId: UserId, deviceId: String? = null) =
        this.asFlow().mapNotNull { store.keys.getKeyVerificationState(it, userId, deviceId) }.firstOrNull()

    private suspend fun SignedCrossSigningKeys.getVerificationState(userId: UserId) =
        this.signed.keys.getVerificationState(userId)

    private suspend fun SignedDeviceKeys.getVerificationState(userId: UserId, deviceId: String) =
        this.signed.keys.getVerificationState(userId, deviceId)

    /**
     * Only DeviceKeys and CrossSigningKeys are supported.
     */
    private inline fun <reified T> Signed<T, UserId>.getSelfSigningKey(): Ed25519Key? {
        return when (val signed = this.signed) {
            is DeviceKeys -> signed.keys.get()
            is CrossSigningKeys -> signed.keys.get()
            else -> null
        }
    }

    private val incomingSecretKeyRequests = MutableStateFlow<Set<SecretKeyRequestEventContent>>(setOf())

    internal fun handleEncryptedIncomingKeyRequests(event: OlmService.DecryptedOlmEvent) {
        val content = event.decrypted.content
        if (event.decrypted.sender == ownUserId && content is SecretKeyRequestEventContent) {
            handleIncomingKeyRequests(Event.ToDeviceEvent(content, event.decrypted.sender))
        }
    }

    internal fun handleIncomingKeyRequests(event: Event<SecretKeyRequestEventContent>) {
        if (event is Event.ToDeviceEvent && event.sender == ownUserId) {
            val content = event.content
            when (content.action) {
                KeyRequestAction.REQUEST -> incomingSecretKeyRequests.update { it + content }
                KeyRequestAction.REQUEST_CANCELLATION -> incomingSecretKeyRequests
                    .update { oldRequests -> oldRequests.filterNot { it.requestId == content.requestId }.toSet() }
            }
        }
    }

    internal suspend fun processIncomingKeyRequests() {
        incomingSecretKeyRequests.value.forEach { request ->
            val senderTrustLevel = store.keys.getDeviceKey(ownUserId, request.requestingDeviceId)?.trustLevel
            if (senderTrustLevel is CrossSigned && senderTrustLevel.verified || senderTrustLevel is Valid && senderTrustLevel.verified) {
                val requestedSecret = request.name
                    ?.let { AllowedSecretType.ofId(it) }
                    ?.let { store.keys.secrets.value[it] }
                if (requestedSecret != null) {
                    log.info { "send incoming key request answer (${request.name}) to device ${request.requestingDeviceId}" }
                    api.users.sendToDevice(
                        mapOf(
                            ownUserId to mapOf(
                                request.requestingDeviceId to SecretKeySendEventContent(
                                    request.requestId, requestedSecret.decryptedPrivateKey
                                )
                            )
                        )
                    ).getOrThrow()
                } else log.info { "got a key request (${request.name}) from ${request.requestingDeviceId}, but we do not have that secret cached" }
            }
            incomingSecretKeyRequests.update { it - request }
        }
    }

    internal suspend fun handleOutgoingKeyRequestAnswer(event: OlmService.DecryptedOlmEvent) {
        val content = event.decrypted.content
        if (event.decrypted.sender == ownUserId && content is SecretKeySendEventContent) {
            log.trace { "handle outgoing key request answer $content" }
            val (senderDeviceId, senderTrustLevel) = store.keys.getDeviceKeys(ownUserId)?.firstNotNullOfOrNull {
                if (it.value.value.get<Ed25519Key>()?.value == event.decrypted.senderKeys.get<Ed25519Key>()?.value)
                    it.key to it.value.trustLevel
                else null
            } ?: (null to null)
            if (senderDeviceId == null) {
                log.warn { "could not derive sender device id from keys ${event.decrypted.senderKeys}" }
                return
            }
            if (!(senderTrustLevel is CrossSigned && senderTrustLevel.verified || senderTrustLevel is Valid && senderTrustLevel.verified)) {
                log.warn { "received a key from $senderDeviceId, but we don't trust that device ($senderTrustLevel)" }
                return
            }
            val request = store.keys.allSecretKeyRequests.value
                .firstOrNull { it.content.requestId == content.requestId }
            if (request?.receiverDeviceIds?.contains(senderDeviceId) != true) {
                log.warn { "received a key from $senderDeviceId, that we did not requested (or request is too old and we already deleted it)" }
                return
            }
            val generatedPublicKey = try {
                freeAfter(OlmPkSigning.create(content.secret)) { it.publicKey }
            } catch (error: Throwable) {
                log.warn(error) { "could not generate public key from received secret" }
                return
            }
            val secretType = request.content.name?.let { AllowedSecretType.ofId(it) }
            val originalPublicKey = when (secretType) {
                M_CROSS_SIGNING_USER_SIGNING ->
                    store.keys.getCrossSigningKey(ownUserId, UserSigningKey)
                        ?.value?.signed?.get<Ed25519Key>()?.value
                M_CROSS_SIGNING_SELF_SIGNING ->
                    store.keys.getCrossSigningKey(ownUserId, SelfSigningKey)
                        ?.value?.signed?.get<Ed25519Key>()?.value
                AllowedSecretType.M_MEGOLM_BACKUP_V1 -> null // TODO
                null -> null
            }
            if (secretType == null || originalPublicKey == null || generatedPublicKey != originalPublicKey) {
                log.warn { "received public key $generatedPublicKey of secret ${request.content.name} did not match the original $originalPublicKey" }
                return
            }
            val encryptedSecret = secretType.getEncrytedSecret()
            if (encryptedSecret == null) {
                log.warn { "could not find encrypted secret" }
                return
            }
            store.keys.secrets.update {
                it + (secretType to StoredSecret(encryptedSecret, content.secret))
            }
            val cancelRequestTo = request.receiverDeviceIds - senderDeviceId
            log.debug { "stored secret $secretType and cancel outgoing key request to $cancelRequestTo" }
            if (cancelRequestTo.isNotEmpty()) {
                api.users.sendToDevice(
                    mapOf(
                        ownUserId to cancelRequestTo.associateWith { request.content.copy(action = KeyRequestAction.REQUEST_CANCELLATION) }
                    )
                ).getOrThrow()
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    internal suspend fun cancelOldOutgoingKeyRequests() {
        store.keys.allSecretKeyRequests.value.forEach {
            if ((it.createdAt + 1.days) < Clock.System.now()) {
                cancelStoredSecretKeyRequest(it)
            }
        }
    }

    private suspend fun cancelStoredSecretKeyRequest(request: StoredSecretKeyRequest) {
        val cancelRequest = request.content.copy(action = KeyRequestAction.REQUEST_CANCELLATION)
        log.info { "cancel old outgoing key request $request" }
        api.users.sendToDevice(mapOf(ownUserId to request.receiverDeviceIds.associateWith { cancelRequest }))
            .onSuccess {
                store.keys.deleteSecretKeyRequest(cancelRequest.requestId)
            }.getOrThrow()
    }

    private suspend fun AllowedSecretType.getEncrytedSecret() = when (this) {
        M_CROSS_SIGNING_USER_SIGNING -> store.globalAccountData.get<UserSigningKeyEventContent>()
        M_CROSS_SIGNING_SELF_SIGNING -> store.globalAccountData.get<SelfSigningKeyEventContent>()
        AllowedSecretType.M_MEGOLM_BACKUP_V1 -> null // TODO
    }

    internal suspend fun requestSecretKeys() {
        val missingSecrets = AllowedSecretType.values()
            .subtract(store.keys.secrets.value.keys)
            .subtract(store.keys.allSecretKeyRequests.value.mapNotNull { request ->
                request.content.name?.let { AllowedSecretType.ofId(it) }
            }.toSet())
        if (missingSecrets.isEmpty()) {
            log.debug { "there are no missing secrets or they are already requested" }
            return
        }
        val receiverDeviceIds = store.keys.getDeviceKeys(ownUserId)
            ?.filter { it.value.trustLevel == CrossSigned(true) }
            ?.map { it.value.value.signed.deviceId }?.minus(ownDeviceId)?.toSet()
        if (receiverDeviceIds.isNullOrEmpty()) {
            log.debug { "there are no receivers, that we can request secret keys from" }
            return
        }
        missingSecrets.map { missingSecret ->
            val requestId = uuid4().toString()
            val request = SecretKeyRequestEventContent(
                name = missingSecret.id,
                action = KeyRequestAction.REQUEST,
                requestingDeviceId = ownDeviceId,
                requestId = requestId
            )
            log.debug { "send secret key request (${missingSecret.id}) to $receiverDeviceIds" }
            api.users.sendToDevice(mapOf(ownUserId to receiverDeviceIds.associateWith { request }))
                .onSuccess {
                    store.keys.addSecretKeyRequest(
                        StoredSecretKeyRequest(request, receiverDeviceIds, Clock.System.now())
                    )
                }.getOrThrow()
        }
    }

    internal suspend fun requestSecretKeysWhenCrossSigned() = coroutineScope {
        api.sync.currentSyncState.retryWhenSyncIsRunning(
            onError = { log.warn(it) { "failed request secrets" } },
            scope = this
        ) {
            store.keys.getDeviceKey(ownUserId, ownDeviceId, this).collect { deviceKeys ->
                if (deviceKeys?.trustLevel == CrossSigned(true)) {
                    requestSecretKeys()
                }
            }
        }
    }

    internal suspend fun handleChangedSecrets(event: Event<out SecretEventContent>) {
        val secretType =
            api.eventContentSerializerMappings.globalAccountData.find { event.content.instanceOf(it.kClass) }
                ?.let { AllowedSecretType.ofId(it.type) }
        if (secretType != null) {
            val storedSecret = store.keys.secrets.value[secretType]
            if (storedSecret?.event != event) {
                store.keys.allSecretKeyRequests.value.filter { it.content.name == secretType.id }
                    .forEach { cancelStoredSecretKeyRequest(it) }
                store.keys.secrets.update { it - secretType }
            }
        }
    }

    internal suspend fun decryptSecret(
        key: ByteArray,
        keyId: String,
        keyInfo: SecretKeyEventContent,
        secretName: String,
        secret: SecretEventContent
    ): String? {
        log.trace { "try decrypt secret $secretName with key $keyId" }
        val encryptedSecret = secret.encrypted[keyId] ?: return null
        return when (keyInfo) {
            is AesHmacSha2Key -> {
                try {
                    val encryptedData = api.json.decodeFromJsonElement<AesHmacSha2EncryptedData>(encryptedSecret)
                    decryptAesHmacSha2(
                        content = encryptedData,
                        key = key,
                        name = secretName
                    ).decodeToString().also { log.debug { "decrypted secret $secretName" } }
                } catch (error: Throwable) {
                    log.warn(error) { "could not decrypt secret $secretName ($secret)" }
                    null
                }
            }
            is SecretKeyEventContent.Unknown -> null
        }
    }

    @OptIn(InternalAPI::class)
    internal suspend fun decryptMissingSecrets(
        key: ByteArray,
        keyId: String,
        keyInfo: SecretKeyEventContent,
    ) {
        val decryptedSecrets = AllowedSecretType.values()
            .subtract(store.keys.secrets.value.keys)
            .mapNotNull { allowedSecret ->
                val event = allowedSecret.getEncrytedSecret()
                if (event != null) {
                    decryptSecret(key, keyId, keyInfo, allowedSecret.id, event.content)
                        ?.let { allowedSecret to StoredSecret(event, it) }
                } else {
                    log.warn { "could not find secret ${allowedSecret.id} to decrypt and cache" }
                    null
                }
            }.toMap()
        store.keys.secrets.update {
            it + decryptedSecrets
        }
    }

    @OptIn(InternalAPI::class)
    internal suspend fun checkOwnAdvertisedMasterKeyAndVerifySelf(
        key: ByteArray,
        keyId: String,
        keyInfo: SecretKeyEventContent
    ): Result<Unit> {
        val encryptedMasterKey = store.globalAccountData.get<MasterKeyEventContent>()?.content
            ?: return Result.failure(MasterKeyInvalidException("could not find encrypted master key"))
        val decryptedPublicKey =
            decryptSecret(key, keyId, keyInfo, "m.cross_signing.master", encryptedMasterKey)
                ?.let { privateKey ->
                    freeAfter(OlmPkSigning.create(privateKey)) { it.publicKey }
                }
        val advertisedPublicKey = store.keys.getCrossSigningKey(ownUserId, MasterKey)?.value?.signed?.get<Ed25519Key>()
        return if (advertisedPublicKey?.value?.decodeUnpaddedBase64Bytes()
                ?.contentEquals(decryptedPublicKey?.decodeUnpaddedBase64Bytes()) == true
        ) {
            val ownDeviceKeys = store.keys.getDeviceKey(ownUserId, ownDeviceId)?.value?.get<Ed25519Key>()
            kotlin.runCatching {
                trustAndSignKeys(setOfNotNull(advertisedPublicKey, ownDeviceKeys), ownUserId)
            }
        } else Result.failure(MasterKeyInvalidException("master public key $decryptedPublicKey did not match the advertised ${advertisedPublicKey?.value}"))
    }

    data class BootstrapCrossSigning(
        val recoveryKey: String,
        val result: Result<UIA<Unit>>,
    )

    @OptIn(InternalAPI::class)
    suspend fun bootstrapCrossSigning(
        recoveryKey: ByteArray = Random.nextBytes(32),
        secretKeyEventContentGenerator: suspend () -> SecretKeyEventContent = {
            val iv = Random.nextBytes(16)
            AesHmacSha2Key(
                iv = iv.encodeBase64(),
                mac = createAesHmacSha2MacFromKey(recoveryKey, iv)
            )
        }
    ): BootstrapCrossSigning {
        log.debug { "bootstrap cross signing" }
        val keyId = generateSequence {
            val alphabet = 'a'..'z'
            generateSequence { alphabet.random() }.take(24).joinToString("")
        }.first { store.globalAccountData.get<SecretKeyEventContent>(key = it) == null }
        val secretKeyEventContent = secretKeyEventContentGenerator()
        return BootstrapCrossSigning(
            recoveryKey = encodeRecoveryKey(recoveryKey),
            result = api.users.setAccountData(secretKeyEventContent, ownUserId, keyId)
                .flatMapResult { api.users.setAccountData(DefaultSecretKeyEventContent(keyId), ownUserId) }
                .flatMapResult {
                    val (masterPrivateKey, masterPublicKey) =
                        freeAfter(OlmPkSigning.create(null)) { it.privateKey to it.publicKey }
                    val masterKey = olm.sign.sign(
                        CrossSigningKeys(
                            userId = ownUserId,
                            usage = setOf(MasterKey),
                            keys = keysOf(Ed25519Key(masterPublicKey, masterPublicKey))
                        ), signWith = SignWith.Custom(privateKey = masterPrivateKey, publicKey = masterPublicKey)
                    )
                    val encryptedMasterKey = MasterKeyEventContent(
                        mapOf(
                            keyId to api.json.encodeToJsonElement(
                                encryptAesHmacSha2(
                                    content = masterPrivateKey.encodeToByteArray(),
                                    key = recoveryKey,
                                    name = "m.cross_signing.master"
                                )
                            )
                        )
                    )
                    val (selfSigningPrivateKey, selfSigningPublicKey) =
                        freeAfter(OlmPkSigning.create(null)) { it.privateKey to it.publicKey }
                    val selfSigningKey = olm.sign.sign(
                        CrossSigningKeys(
                            userId = ownUserId,
                            usage = setOf(SelfSigningKey),
                            keys = keysOf(Ed25519Key(selfSigningPublicKey, selfSigningPublicKey))
                        ), signWith = SignWith.Custom(privateKey = masterPrivateKey, publicKey = masterPublicKey)
                    )
                    val encryptedSelfSigningKey = SelfSigningKeyEventContent(
                        mapOf(
                            keyId to api.json.encodeToJsonElement(
                                encryptAesHmacSha2(
                                    content = selfSigningPrivateKey.encodeToByteArray(),
                                    key = recoveryKey,
                                    name = M_CROSS_SIGNING_SELF_SIGNING.id
                                )
                            )
                        )
                    )
                    val (userSigningPrivateKey, userSigningPublicKey) =
                        freeAfter(OlmPkSigning.create(null)) { it.privateKey to it.publicKey }
                    val userSigningKey = olm.sign.sign(
                        CrossSigningKeys(
                            userId = ownUserId,
                            usage = setOf(UserSigningKey),
                            keys = keysOf(Ed25519Key(userSigningPublicKey, userSigningPublicKey))
                        ), signWith = SignWith.Custom(privateKey = masterPrivateKey, publicKey = masterPublicKey)
                    )
                    val encryptedUserSigningKey = UserSigningKeyEventContent(
                        mapOf(
                            keyId to api.json.encodeToJsonElement(
                                encryptAesHmacSha2(
                                    content = userSigningPrivateKey.encodeToByteArray(),
                                    key = recoveryKey,
                                    name = M_CROSS_SIGNING_USER_SIGNING.id
                                )
                            )
                        )
                    )
                    store.keys.secrets.update {
                        mapOf(
                            M_CROSS_SIGNING_SELF_SIGNING to StoredSecret(
                                Event.GlobalAccountDataEvent(encryptedSelfSigningKey),
                                selfSigningPrivateKey
                            ),
                            M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                                Event.GlobalAccountDataEvent(encryptedUserSigningKey),
                                userSigningPrivateKey
                            ),
                        )
                    }
                    api.users.setAccountData(encryptedMasterKey, ownUserId)
                        .flatMapResult { api.users.setAccountData(encryptedUserSigningKey, ownUserId) }
                        .flatMapResult { api.users.setAccountData(encryptedSelfSigningKey, ownUserId) }
                        .flatMapResult {
                            api.keys.setCrossSigningKeys(
                                masterKey = masterKey,
                                selfSigningKey = selfSigningKey,
                                userSigningKey = userSigningKey
                            )
                        }
                }.map {
                    it.injectOnSuccessIntoUIA {
                        store.keys.outdatedKeys.update { oldOutdatedKeys -> oldOutdatedKeys + ownUserId }
                        store.keys.waitForUpdateOutdatedKey(ownUserId)
                        val masterKey =
                            store.keys.getCrossSigningKey(ownUserId, MasterKey)?.value?.signed?.get<Ed25519Key>()
                        val ownDeviceKey = store.keys.getDeviceKey(ownUserId, ownDeviceId)?.value?.get<Ed25519Key>()

                        trustAndSignKeys(setOfNotNull(masterKey, ownDeviceKey), ownUserId)
                        log.debug { "finished bootstrapping" }
                    }
                }
        )
    }

    @OptIn(InternalAPI::class)
    suspend fun bootstrapCrossSigningFromPassphrase(
        passphrase: String,
        secretKeyEventContentGenerator: suspend () -> Pair<ByteArray, SecretKeyEventContent> = {
            val passphraseInfo = Pbkdf2(
                salt = Random.nextBytes(32).encodeBase64(),
                iterations = 500_000,
                bits = 32 * 8
            )
            val iv = Random.nextBytes(16)
            val key = recoveryKeyFromPassphrase(passphrase, passphraseInfo).getOrThrow()
            key to AesHmacSha2Key(
                passphrase = passphraseInfo,
                iv = iv.encodeBase64(),
                mac = createAesHmacSha2MacFromKey(key = key, iv = iv)
            )
        }
    ): BootstrapCrossSigning {
        val secretKeyEventContent = secretKeyEventContentGenerator()
        return bootstrapCrossSigning(secretKeyEventContent.first) { secretKeyEventContent.second }
    }

    /**
     * @return the trust level of a device.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun getTrustLevel(
        userId: UserId,
        deviceId: String,
        scope: CoroutineScope
    ): StateFlow<DeviceTrustLevel> {
        return store.keys.getDeviceKey(userId, deviceId, scope).map { deviceKeys ->
            when (val trustLevel = deviceKeys?.trustLevel) {
                is Valid -> if (trustLevel.verified) DeviceTrustLevel.Verified else DeviceTrustLevel.NotVerified
                is CrossSigned -> if (trustLevel.verified) DeviceTrustLevel.Verified else DeviceTrustLevel.NotVerified
                NotCrossSigned -> DeviceTrustLevel.NotCrossSigned
                Blocked -> DeviceTrustLevel.Blocked
                is Invalid -> DeviceTrustLevel.Invalid(trustLevel.reason)
                is NotAllDeviceKeysCrossSigned, null -> DeviceTrustLevel.Invalid("could not determine trust level of device key: $deviceKeys")
            }
        }.stateIn(scope)
    }

    /**
     * @return the trust level of a device or null, if the timeline event is not a megolm encrypted event.
     */
    suspend fun getTrustLevel(
        timelineEvent: TimelineEvent,
        scope: CoroutineScope
    ): StateFlow<DeviceTrustLevel>? {
        val event = timelineEvent.event
        val content = event.content
        return if (event is Event.MessageEvent && content is EncryptedEventContent.MegolmEncryptedEventContent) {
            return getTrustLevel(event.sender, content.deviceId, scope)
        } else null
    }

    /**
     * @return the trust level of a user. This will only be present, if the requested user has cross signing enabled.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun getTrustLevel(
        userId: UserId,
        scope: CoroutineScope
    ): StateFlow<UserTrustLevel> {
        return store.keys.getCrossSigningKeys(userId, scope)
            .map { keys -> keys?.firstOrNull { it.value.signed.usage.contains(MasterKey) } }
            .map { crossSigningKeys ->
                when (val trustLevel = crossSigningKeys?.trustLevel) {
                    is Valid -> UserTrustLevel.CrossSigned(trustLevel.verified)
                    is CrossSigned -> UserTrustLevel.CrossSigned(trustLevel.verified)
                    is NotAllDeviceKeysCrossSigned -> UserTrustLevel.NotAllDevicesCrossSigned(trustLevel.verified)
                    Blocked -> UserTrustLevel.Blocked
                    is Invalid -> UserTrustLevel.Invalid(trustLevel.reason)
                    NotCrossSigned, null -> UserTrustLevel.Invalid("could not determine trust level of cross signing key: $crossSigningKeys")
                }
            }.stateIn(scope)
    }

    suspend fun getDeviceKeys(
        userId: UserId,
        scope: CoroutineScope,
    ): StateFlow<List<DeviceKeys>?> {
        return store.keys.getDeviceKeys(userId, scope).map {
            it?.values?.map { storedDeviceKeys -> storedDeviceKeys.value.signed }
        }.stateIn(scope)
    }
}