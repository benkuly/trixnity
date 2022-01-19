package net.folivo.trixnity.client.key

import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.encodeToJsonElement
import mu.KotlinLogging
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.SyncApiClient.SyncState.INITIAL_SYNC
import net.folivo.trixnity.client.api.SyncApiClient.SyncState.RUNNING
import net.folivo.trixnity.client.api.UIA
import net.folivo.trixnity.client.api.injectOnSuccessIntoUIA
import net.folivo.trixnity.client.api.model.sync.SyncResponse
import net.folivo.trixnity.client.crypto.*
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.*
import net.folivo.trixnity.client.crypto.OlmSignService.SignWith
import net.folivo.trixnity.client.retryWhenSyncIs
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.AllowedSecretType.M_CROSS_SIGNING_SELF_SIGNING
import net.folivo.trixnity.client.store.AllowedSecretType.M_CROSS_SIGNING_USER_SIGNING
import net.folivo.trixnity.client.verification.KeyVerificationState
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.crosssigning.MasterKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.SecretStorageKeyPassphrase.Pbkdf2
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage.*
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.olm.OlmPkSigning
import net.folivo.trixnity.olm.decodeUnpaddedBase64Bytes
import net.folivo.trixnity.olm.freeAfter
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.flatMap
import kotlin.collections.toSet
import kotlin.random.Random
import arrow.core.flatMap as flatMapResult

private val log = KotlinLogging.logger {}

class KeyService(
    private val ownUserId: UserId,
    private val ownDeviceId: String,
    private val store: Store,
    private val olm: OlmService,
    private val api: MatrixApiClient,
) {
    internal val secret = KeySecretService(ownUserId, ownDeviceId, store, olm, api)

    @OptIn(FlowPreview::class)
    internal suspend fun start(scope: CoroutineScope) {
        api.sync.subscribeSyncResponse { syncResponse ->
            syncResponse.deviceLists?.also { handleDeviceLists(it) }
        }
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        scope.launch(start = CoroutineStart.UNDISPATCHED) { handleOutdatedKeys() }
        secret.start(scope)
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
        api.sync.currentSyncState.retryWhenSyncIs(
            RUNNING, INITIAL_SYNC,
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

    @OptIn(InternalAPI::class)
    internal suspend fun checkOwnAdvertisedMasterKeyAndVerifySelf(
        key: ByteArray,
        keyId: String,
        keyInfo: SecretKeyEventContent
    ): Result<Unit> {
        val encryptedMasterKey = store.globalAccountData.get<MasterKeyEventContent>()?.content
            ?: return Result.failure(MasterKeyInvalidException("could not find encrypted master key"))
        val decryptedPublicKey =
            secret.decryptSecret(key, keyId, keyInfo, "m.cross_signing.master", encryptedMasterKey)
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