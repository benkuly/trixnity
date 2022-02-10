package net.folivo.trixnity.client.key

import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mu.KotlinLogging
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.SyncApiClient.SyncState.*
import net.folivo.trixnity.client.api.UIA
import net.folivo.trixnity.client.api.injectOnSuccessIntoUIA
import net.folivo.trixnity.client.api.model.sync.SyncResponse
import net.folivo.trixnity.client.crypto.*
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.*
import net.folivo.trixnity.client.crypto.OlmSignService.SignWith
import net.folivo.trixnity.client.retryInfiniteWhenSyncIs
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.AllowedSecretType.M_CROSS_SIGNING_SELF_SIGNING
import net.folivo.trixnity.client.store.AllowedSecretType.M_CROSS_SIGNING_USER_SIGNING
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
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import arrow.core.flatMap as flatMapResult

private val log = KotlinLogging.logger {}

class KeyService(
    olmPickleKey: String,
    private val ownUserId: UserId,
    private val ownDeviceId: String,
    private val store: Store,
    private val olm: OlmService,
    private val api: MatrixApiClient,
    internal val secret: KeySecretService = KeySecretService(ownUserId, ownDeviceId, store, olm, api),
    internal val backup: KeyBackupService = KeyBackupService(olmPickleKey, ownUserId, ownDeviceId, store, api, olm),
    internal val trust: KeyTrustService = KeyTrustService(ownUserId, store, olm, api)
) {

    internal suspend fun start(scope: CoroutineScope) {
        api.sync.subscribeDeviceLists(::handleDeviceLists)
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        scope.launch(start = CoroutineStart.UNDISPATCHED) { handleOutdatedKeys() }
        secret.start(scope)
        backup.start(scope)
    }

    internal suspend fun handleDeviceLists(deviceList: SyncResponse.DeviceLists?) {
        if (deviceList == null) return
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
        api.sync.currentSyncState.retryInfiniteWhenSyncIs(
            STARTED, INITIAL_SYNC, RUNNING,
            scheduleLimit = 30.seconds,
            onError = { log.warn(it) { "failed update outdated keys" } },
            onCancel = { log.info { "stop update outdated keys, because job was cancelled" } },
            scope = this
        ) {
            store.keys.outdatedKeys.collect { userIds ->
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
        val signatureVerification =
            olm.sign.verify(crossSigningKey, mapOf(userId to setOfNotNull(signingKeyForVerification)))
        if (signatureVerification == VerifyResult.Valid
            || signingOptional && signatureVerification is VerifyResult.MissingSignature
        ) {
            val oldTrustLevel = store.keys.getCrossSigningKey(userId, usage)?.trustLevel
            val trustLevel = trust.calculateCrossSigningKeysTrustLevel(crossSigningKey)
            log.debug { "updated outdated cross signing ${usage.name} key of user $userId with trust level $trustLevel (was $oldTrustLevel)" }
            val newKey = StoredCrossSigningKeys(crossSigningKey, trustLevel)
            store.keys.updateCrossSigningKeys(userId) { oldKeys ->
                ((oldKeys?.filterNot { it.value.signed.usage.contains(usage) }
                    ?.toSet() ?: setOf())
                        + newKey)
            }
            if (oldTrustLevel != trustLevel) {
                newKey.value.signed.get<Ed25519Key>()?.let { trust.updateTrustLevelOfKeyChainSignedBy(userId, it) }
            }
        } else {
            log.warn { "Signatures from cross signing key (${usage.name}) of $userId were not valid: $signatureVerification!" }
        }
    }

    private suspend fun handleOutdatedDeviceKeys(
        userId: UserId,
        devices: Map<String, SignedDeviceKeys>,
        joinedEncryptedRooms: Deferred<List<RoomId>>
    ) {
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
            val trustLevel = trust.calculateDeviceKeysTrustLevel(deviceKeys)
            log.debug { "updated outdated device keys ${deviceKeys.signed.deviceId} of user $userId with trust level $trustLevel" }
            StoredDeviceKeys(deviceKeys, trustLevel)
        }
        val addedDeviceKeys = if (oldDevices != null) newDevices.keys - oldDevices.keys else newDevices.keys
        if (addedDeviceKeys.isNotEmpty()) {
            joinedEncryptedRooms.await()
                .filter { roomId ->
                    store.roomState.getByStateKey<MemberEventContent>(roomId, userId.full)
                        ?.content?.membership.let { it == MemberEventContent.Membership.JOIN || it == MemberEventContent.Membership.INVITE }
                }.also {
                    if (it.isNotEmpty()) log.debug { "notify megolm sessions in rooms $it about new device keys from $userId: $addedDeviceKeys" }
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
            decryptSecret(key, keyId, keyInfo, "m.cross_signing.master", encryptedMasterKey, api.json)
                ?.let { privateKey ->
                    freeAfter(OlmPkSigning.create(privateKey)) { it.publicKey }
                }
        val advertisedPublicKey = store.keys.getCrossSigningKey(ownUserId, MasterKey)?.value?.signed?.get<Ed25519Key>()
        return if (advertisedPublicKey?.value?.decodeUnpaddedBase64Bytes()
                ?.contentEquals(decryptedPublicKey?.decodeUnpaddedBase64Bytes()) == true
        ) {
            val ownDeviceKeys = store.keys.getDeviceKey(ownUserId, ownDeviceId)?.value?.get<Ed25519Key>()
            kotlin.runCatching {
                trust.trustAndSignKeys(setOfNotNull(advertisedPublicKey, ownDeviceKeys), ownUserId)
            }
        } else Result.failure(MasterKeyInvalidException("master public key $decryptedPublicKey did not match the advertised ${advertisedPublicKey?.value}"))
    }

    data class BootstrapCrossSigning(
        val recoveryKey: String,
        val result: Result<UIA<Unit>>,
    )

    /**
     * This allows you to bootstrap cross signing. Be aware, that this could override an existing cross signing setup of
     * the account. Be aware, that this also creates a new key backup, which could replace an existing key backup.
     */
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
                    val (masterSigningPrivateKey, masterSigningPublicKey) =
                        freeAfter(OlmPkSigning.create(null)) { it.privateKey to it.publicKey }
                    val masterSigningKey = olm.sign.sign(
                        CrossSigningKeys(
                            userId = ownUserId,
                            usage = setOf(MasterKey),
                            keys = keysOf(Ed25519Key(masterSigningPublicKey, masterSigningPublicKey))
                        ),
                        signWith = SignWith.Custom(
                            privateKey = masterSigningPrivateKey,
                            publicKey = masterSigningPublicKey
                        )
                    )
                    val encryptedMasterSigningKey = MasterKeyEventContent(
                        encryptSecret(recoveryKey, keyId, "m.cross_signing.master", masterSigningPrivateKey, api.json)
                    )
                    val (selfSigningPrivateKey, selfSigningPublicKey) =
                        freeAfter(OlmPkSigning.create(null)) { it.privateKey to it.publicKey }
                    val selfSigningKey = olm.sign.sign(
                        CrossSigningKeys(
                            userId = ownUserId,
                            usage = setOf(SelfSigningKey),
                            keys = keysOf(Ed25519Key(selfSigningPublicKey, selfSigningPublicKey))
                        ),
                        signWith = SignWith.Custom(
                            privateKey = masterSigningPrivateKey,
                            publicKey = masterSigningPublicKey
                        )
                    )
                    val encryptedSelfSigningKey = SelfSigningKeyEventContent(
                        encryptSecret(
                            recoveryKey,
                            keyId,
                            M_CROSS_SIGNING_SELF_SIGNING.id,
                            selfSigningPrivateKey,
                            api.json
                        )
                    )
                    val (userSigningPrivateKey, userSigningPublicKey) =
                        freeAfter(OlmPkSigning.create(null)) { it.privateKey to it.publicKey }
                    val userSigningKey = olm.sign.sign(
                        CrossSigningKeys(
                            userId = ownUserId,
                            usage = setOf(UserSigningKey),
                            keys = keysOf(Ed25519Key(userSigningPublicKey, userSigningPublicKey))
                        ),
                        signWith = SignWith.Custom(
                            privateKey = masterSigningPrivateKey,
                            publicKey = masterSigningPublicKey
                        )
                    )
                    val encryptedUserSigningKey = UserSigningKeyEventContent(
                        encryptSecret(
                            recoveryKey,
                            keyId,
                            M_CROSS_SIGNING_USER_SIGNING.id,
                            userSigningPrivateKey,
                            api.json
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
                    api.users.setAccountData(encryptedMasterSigningKey, ownUserId)
                        .flatMapResult { api.users.setAccountData(encryptedUserSigningKey, ownUserId) }
                        .flatMapResult { api.users.setAccountData(encryptedSelfSigningKey, ownUserId) }
                        .flatMapResult {
                            backup.bootstrapRoomKeyBackup(
                                recoveryKey,
                                keyId,
                                masterSigningPrivateKey,
                                masterSigningPublicKey
                            )
                        }
                        .flatMapResult {
                            api.keys.setCrossSigningKeys(
                                masterKey = masterSigningKey,
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

                        trust.trustAndSignKeys(setOfNotNull(masterKey, ownDeviceKey), ownUserId)
                        log.debug { "finished bootstrapping" }
                    }
                }
        )
    }

    /**
     * This allows you to bootstrap cross signing. Be aware, that this could override an existing cross signing setup of
     * the account. Be aware, that this also creates a new key backup, which could replace an existing key backup.
     */
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
            val megolmKeyIsTrusted =
                store.olm.getInboundMegolmSession(content.senderKey, content.sessionId, event.roomId)?.isTrusted == true
            return if (megolmKeyIsTrusted) getTrustLevel(event.sender, content.deviceId, scope)
            else MutableStateFlow(DeviceTrustLevel.NotVerified)
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
                    NotCrossSigned -> UserTrustLevel.Invalid("could not determine trust level of cross signing key: $crossSigningKeys")
                    null -> UserTrustLevel.Unknown
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