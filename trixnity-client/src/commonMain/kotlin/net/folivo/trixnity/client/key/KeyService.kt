package net.folivo.trixnity.client.key

import com.soywiz.krypto.SecureRandom
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mu.KotlinLogging
import net.folivo.trixnity.client.key.KeySignatureTrustLevel.*
import net.folivo.trixnity.client.retryInfiniteWhenSyncIs
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.client.SyncState.*
import net.folivo.trixnity.clientserverapi.client.UIA
import net.folivo.trixnity.clientserverapi.client.injectOnSuccessIntoUIA
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.crosssigning.MasterKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.Membership.INVITE
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage.*
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.crypto.createAesHmacSha2MacFromKey
import net.folivo.trixnity.crypto.sign.*
import net.folivo.trixnity.olm.OlmPkSigning
import net.folivo.trixnity.olm.decodeUnpaddedBase64Bytes
import net.folivo.trixnity.olm.freeAfter
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import arrow.core.flatMap as flatMapResult

private val log = KotlinLogging.logger {}

interface IKeyService {
    val backup: IKeyBackupService
    val trust: IKeyTrustService
    val secret: IKeySecretService

    val bootstrapRunning: StateFlow<Boolean>

    data class BootstrapCrossSigning(
        val recoveryKey: String,
        val result: Result<UIA<Unit>>,
    )

    /**
     * This allows you to bootstrap cross signing. Be aware, that this could override an existing cross signing setup of
     * the account. Be aware, that this also creates a new key backup, which could replace an existing key backup.
     */
    suspend fun bootstrapCrossSigning(
        recoveryKey: ByteArray = SecureRandom.nextBytes(32),
        secretKeyEventContentGenerator: suspend () -> SecretKeyEventContent = {
            val iv = SecureRandom.nextBytes(16)
            AesHmacSha2Key(
                iv = iv.encodeBase64(),
                mac = createAesHmacSha2MacFromKey(recoveryKey, iv)
            )
        }
    ): BootstrapCrossSigning

    /**
     * This allows you to bootstrap cross signing. Be aware, that this could override an existing cross signing setup of
     * the account. Be aware, that this also creates a new key backup, which could replace an existing key backup.
     */
    suspend fun bootstrapCrossSigningFromPassphrase(
        passphrase: String,
        secretKeyEventContentGenerator: suspend () -> Pair<ByteArray, SecretKeyEventContent> = {
            val passphraseInfo = AesHmacSha2Key.SecretStorageKeyPassphrase.Pbkdf2(
                salt = SecureRandom.nextBytes(32).encodeBase64(),
                iterations = 120_000,
                bits = 32 * 8
            )
            val iv = SecureRandom.nextBytes(16)
            val key = recoveryKeyFromPassphrase(passphrase, passphraseInfo).getOrThrow()
            key to AesHmacSha2Key(
                passphrase = passphraseInfo,
                iv = iv.encodeBase64(),
                mac = createAesHmacSha2MacFromKey(key = key, iv = iv)
            )
        }
    ): BootstrapCrossSigning

    /**
     * @return the trust level of a device.
     */
    suspend fun getTrustLevel(
        userId: UserId,
        deviceId: String,
        scope: CoroutineScope
    ): Flow<DeviceTrustLevel>

    /**
     * @return the trust level of a device or null, if the timeline event is not a megolm encrypted event.
     */
    suspend fun getTrustLevel(
        timelineEvent: TimelineEvent,
        scope: CoroutineScope
    ): Flow<DeviceTrustLevel>?

    /**
     * @return the trust level of a user. This will only be present, if the requested user has cross signing enabled.
     */
    suspend fun getTrustLevel(
        userId: UserId,
        scope: CoroutineScope
    ): Flow<UserTrustLevel>

    suspend fun getDeviceKeys(
        userId: UserId,
        scope: CoroutineScope,
    ): Flow<List<DeviceKeys>?>

    suspend fun getDeviceKeys(
        userId: UserId,
    ): List<DeviceKeys>?

    suspend fun getCrossSigningKeys(
        userId: UserId,
        scope: CoroutineScope,
    ): Flow<List<CrossSigningKeys>?>

    suspend fun getCrossSigningKeys(
        userId: UserId,
    ): List<CrossSigningKeys>?

    suspend fun checkOwnAdvertisedMasterKeyAndVerifySelf(
        key: ByteArray,
        keyId: String,
        keyInfo: SecretKeyEventContent
    ): Result<Unit>
}

class KeyService(
    private val ownUserId: UserId,
    private val ownDeviceId: String,
    private val store: Store,
    private val signService: ISignService,
    private val api: MatrixClientServerApiClient,
    private val currentSyncState: StateFlow<SyncState>,
    override val secret: IKeySecretService,
    override val backup: IKeyBackupService,
    override val trust: IKeyTrustService = KeyTrustService(ownUserId, store, signService, api),
    scope: CoroutineScope,
) : IKeyService {

    init {
        api.sync.subscribeDeviceLists(::handleDeviceLists)
        api.sync.subscribe(::handleMemberEvents)
        api.sync.subscribe(::handleEncryptionEvents)
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        scope.launch(start = CoroutineStart.UNDISPATCHED) { handleOutdatedKeys() }
    }

    internal suspend fun handleMemberEvents(event: Event<MemberEventContent>) {
        if (event is Event.StateEvent && store.room.get(event.roomId).value?.encryptionAlgorithm == EncryptionAlgorithm.Megolm) {
            log.debug { "handle membership change in an encrypted room" }
            val userId = UserId(event.stateKey)
            when (event.content.membership) {
                Membership.LEAVE, Membership.BAN -> {
                    if (store.room.encryptedJoinedRooms().find { roomId ->
                            store.roomState.getByStateKey<MemberEventContent>(roomId, event.stateKey)
                                ?.content?.membership.let { it == JOIN || it == INVITE }
                        } == null)
                        store.keys.updateDeviceKeys(userId) { null }
                }
                JOIN, INVITE -> {
                    if (event.unsigned?.previousContent?.membership != event.content.membership
                        && store.keys.getDeviceKeys(userId) == null
                    ) store.keys.outdatedKeys.update { it + userId }
                }
                else -> {
                }
            }
        }
    }

    internal suspend fun handleEncryptionEvents(event: Event<EncryptionEventContent>) {
        if (event is Event.StateEvent) {
            val outdatedKeys = store.roomState.members(event.roomId, JOIN, INVITE).filterNot {
                store.keys.isTracked(it)
            }
            store.keys.outdatedKeys.update { it + outdatedKeys }
        }
    }

    internal suspend fun handleDeviceLists(deviceList: Sync.Response.DeviceLists?) {
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

    internal suspend fun handleOutdatedKeys() {
        currentSyncState.retryInfiniteWhenSyncIs(
            STARTED, INITIAL_SYNC, RUNNING,
            scheduleLimit = 30.seconds,
            onError = { log.warn(it) { "failed update outdated keys" } },
            onCancel = { log.info { "stop update outdated keys, because job was cancelled" } },
        ) {
            coroutineScope {
                store.keys.outdatedKeys.collect { userIds ->
                    if (userIds.isNotEmpty()) {
                        log.debug { "try update outdated keys of $userIds" }
                        val keysResponse = api.keys.getKeys(
                            deviceKeys = userIds.associateWith { emptySet() },
                            token = store.account.syncBatchToken.value
                        ).getOrThrow()

                        keysResponse.masterKeys?.forEach { (userId, masterKey) ->
                            handleOutdatedCrossSigningKey(
                                userId,
                                masterKey,
                                MasterKey,
                                masterKey.getSelfSigningKey(),
                                true
                            )
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
                        val joinedEncryptedRooms =
                            async(start = CoroutineStart.LAZY) { store.room.encryptedJoinedRooms() }
                        keysResponse.deviceKeys?.forEach { (userId, devices) ->
                            handleOutdatedDeviceKeys(userId, devices, joinedEncryptedRooms)
                        }
                        joinedEncryptedRooms.cancel()

                        // indicate, that we fetched the keys of the user
                        userIds.forEach { userId ->
                            store.keys.updateCrossSigningKeys(userId) { it ?: setOf() }
                            store.keys.updateDeviceKeys(userId) { it ?: mapOf() }
                        }

                        store.keys.outdatedKeys.update { it - userIds }
                    }
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
            signService.verify(crossSigningKey, mapOf(userId to setOfNotNull(signingKeyForVerification)))
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
                signService.verify(deviceKeys, mapOf(userId to setOfNotNull(deviceKeys.getSelfSigningKey())))
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
                        ?.content?.membership.let { it == JOIN || it == INVITE }
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

    override suspend fun checkOwnAdvertisedMasterKeyAndVerifySelf(
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

    private val _bootstrapRunning = MutableStateFlow(false)
    override val bootstrapRunning = _bootstrapRunning.asStateFlow()

    override suspend fun bootstrapCrossSigning(
        recoveryKey: ByteArray,
        secretKeyEventContentGenerator: suspend () -> SecretKeyEventContent
    ): IKeyService.BootstrapCrossSigning {
        log.debug { "bootstrap cross signing" }
        _bootstrapRunning.value = true

        Random.Default
        val keyId = generateSequence {
            val alphabet = 'a'..'z'
            generateSequence { alphabet.random() }.take(24).joinToString("")
        }.first { store.globalAccountData.get<SecretKeyEventContent>(key = it) == null }
        val secretKeyEventContent = secretKeyEventContentGenerator()
        return IKeyService.BootstrapCrossSigning(
            recoveryKey = encodeRecoveryKey(recoveryKey),
            result = api.users.setAccountData(secretKeyEventContent, ownUserId, keyId)
                .flatMapResult { api.users.setAccountData(DefaultSecretKeyEventContent(keyId), ownUserId) }
                .flatMapResult {
                    val (masterSigningPrivateKey, masterSigningPublicKey) =
                        freeAfter(OlmPkSigning.create(null)) { it.privateKey to it.publicKey }
                    val masterSigningKey = signService.sign(
                        CrossSigningKeys(
                            userId = ownUserId,
                            usage = setOf(MasterKey),
                            keys = keysOf(Ed25519Key(masterSigningPublicKey, masterSigningPublicKey))
                        ),
                        signWith = SignWith.PrivateKey(
                            privateKey = masterSigningPrivateKey,
                            publicKey = masterSigningPublicKey
                        )
                    )
                    val encryptedMasterSigningKey = MasterKeyEventContent(
                        encryptSecret(recoveryKey, keyId, "m.cross_signing.master", masterSigningPrivateKey, api.json)
                    )
                    val (selfSigningPrivateKey, selfSigningPublicKey) =
                        freeAfter(OlmPkSigning.create(null)) { it.privateKey to it.publicKey }
                    val selfSigningKey = signService.sign(
                        CrossSigningKeys(
                            userId = ownUserId,
                            usage = setOf(SelfSigningKey),
                            keys = keysOf(Ed25519Key(selfSigningPublicKey, selfSigningPublicKey))
                        ),
                        signWith = SignWith.PrivateKey(
                            privateKey = masterSigningPrivateKey,
                            publicKey = masterSigningPublicKey
                        )
                    )
                    val encryptedSelfSigningKey = SelfSigningKeyEventContent(
                        encryptSecret(
                            recoveryKey,
                            keyId,
                            SecretType.M_CROSS_SIGNING_SELF_SIGNING.id,
                            selfSigningPrivateKey,
                            api.json
                        )
                    )
                    val (userSigningPrivateKey, userSigningPublicKey) =
                        freeAfter(OlmPkSigning.create(null)) { it.privateKey to it.publicKey }
                    val userSigningKey = signService.sign(
                        CrossSigningKeys(
                            userId = ownUserId,
                            usage = setOf(UserSigningKey),
                            keys = keysOf(Ed25519Key(userSigningPublicKey, userSigningPublicKey))
                        ),
                        signWith = SignWith.PrivateKey(
                            privateKey = masterSigningPrivateKey,
                            publicKey = masterSigningPublicKey
                        )
                    )
                    val encryptedUserSigningKey = UserSigningKeyEventContent(
                        encryptSecret(
                            recoveryKey,
                            keyId,
                            SecretType.M_CROSS_SIGNING_USER_SIGNING.id,
                            userSigningPrivateKey,
                            api.json
                        )
                    )
                    store.keys.secrets.update {
                        mapOf(
                            SecretType.M_CROSS_SIGNING_SELF_SIGNING to StoredSecret(
                                Event.GlobalAccountDataEvent(encryptedSelfSigningKey),
                                selfSigningPrivateKey
                            ),
                            SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
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
                }.mapCatching {
                    it.injectOnSuccessIntoUIA {
                        store.keys.outdatedKeys.update { oldOutdatedKeys -> oldOutdatedKeys + ownUserId }
                        store.keys.waitForUpdateOutdatedKey(ownUserId)
                        val masterKey =
                            store.keys.getCrossSigningKey(ownUserId, MasterKey)?.value?.signed?.get<Ed25519Key>()
                        val ownDeviceKey = store.keys.getDeviceKey(ownUserId, ownDeviceId)?.value?.get<Ed25519Key>()

                        trust.trustAndSignKeys(setOfNotNull(masterKey, ownDeviceKey), ownUserId)
                        _bootstrapRunning.value = false
                        log.debug { "finished bootstrapping" }
                    }
                }
        )
    }

    override suspend fun bootstrapCrossSigningFromPassphrase(
        passphrase: String,
        secretKeyEventContentGenerator: suspend () -> Pair<ByteArray, SecretKeyEventContent>
    ): IKeyService.BootstrapCrossSigning {
        val secretKeyEventContent = secretKeyEventContentGenerator()
        return bootstrapCrossSigning(secretKeyEventContent.first) { secretKeyEventContent.second }
    }

    override suspend fun getTrustLevel(
        userId: UserId,
        deviceId: String,
        scope: CoroutineScope
    ): Flow<DeviceTrustLevel> {
        return store.keys.getDeviceKey(userId, deviceId, scope).map { deviceKeys ->
            when (val trustLevel = deviceKeys?.trustLevel) {
                is Valid -> if (trustLevel.verified) DeviceTrustLevel.Verified else DeviceTrustLevel.NotVerified
                is CrossSigned -> if (trustLevel.verified) DeviceTrustLevel.Verified else DeviceTrustLevel.NotVerified
                NotCrossSigned -> DeviceTrustLevel.NotCrossSigned
                Blocked -> DeviceTrustLevel.Blocked
                is Invalid -> DeviceTrustLevel.Invalid(trustLevel.reason)
                is NotAllDeviceKeysCrossSigned, null -> DeviceTrustLevel.Invalid("could not determine trust level of device key: $deviceKeys")
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getTrustLevel(
        timelineEvent: TimelineEvent,
        scope: CoroutineScope
    ): Flow<DeviceTrustLevel>? {
        val event = timelineEvent.event
        val content = event.content
        return if (event is Event.MessageEvent && content is EncryptedEventContent.MegolmEncryptedEventContent) {
            combine(
                store.olm.getInboundMegolmSession(content.sessionId, event.roomId, scope),
                store.keys.getDeviceKeys(event.sender, scope)
            ) { megolmSession, deviceKeys ->
                if (megolmSession == null || deviceKeys == null || megolmSession.isTrusted.not())
                    flowOf(DeviceTrustLevel.NotVerified)
                else {
                    val deviceId =
                        deviceKeys.values.find { it.value.signed.keys.keys.contains(megolmSession.senderKey) }
                            ?.value?.signed?.deviceId
                    if (deviceId != null) getTrustLevel(event.sender, deviceId, scope)
                    else flowOf(DeviceTrustLevel.NotVerified)
                }
            }.flatMapLatest { it }
        } else null
    }

    override suspend fun getTrustLevel(
        userId: UserId,
        scope: CoroutineScope
    ): Flow<UserTrustLevel> {
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
            }
    }

    override suspend fun getDeviceKeys(
        userId: UserId,
        scope: CoroutineScope,
    ): Flow<List<DeviceKeys>?> {
        return store.keys.getDeviceKeys(userId, scope).map {
            it?.values?.map { storedKeys -> storedKeys.value.signed }
        }
    }

    override suspend fun getDeviceKeys(
        userId: UserId,
    ): List<DeviceKeys>? {
        return store.keys.getDeviceKeys(userId)?.values?.map { it.value.signed }
    }

    override suspend fun getCrossSigningKeys(
        userId: UserId,
        scope: CoroutineScope,
    ): Flow<List<CrossSigningKeys>?> {
        return store.keys.getCrossSigningKeys(userId, scope).map {
            it?.map { storedKeys -> storedKeys.value.signed }
        }
    }

    override suspend fun getCrossSigningKeys(
        userId: UserId,
    ): List<CrossSigningKeys>? {
        return store.keys.getCrossSigningKeys(userId)?.map { it.value.signed }
    }
}