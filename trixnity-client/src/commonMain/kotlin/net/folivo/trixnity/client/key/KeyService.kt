package net.folivo.trixnity.client.key

import com.soywiz.krypto.SecureRandom
import io.ktor.util.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import mu.KotlinLogging
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.KeySignatureTrustLevel.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.UIA
import net.folivo.trixnity.clientserverapi.client.injectOnSuccessIntoUIA
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.crosssigning.MasterKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key
import net.folivo.trixnity.core.model.keys.CrossSigningKeys
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage.*
import net.folivo.trixnity.core.model.keys.DeviceKeys
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.crypto.createAesHmacSha2MacFromKey
import net.folivo.trixnity.crypto.key.encodeRecoveryKey
import net.folivo.trixnity.crypto.key.encryptSecret
import net.folivo.trixnity.crypto.key.recoveryKeyFromPassphrase
import net.folivo.trixnity.crypto.sign.SignService
import net.folivo.trixnity.crypto.sign.SignWith
import net.folivo.trixnity.crypto.sign.sign
import net.folivo.trixnity.olm.OlmPkSigning
import net.folivo.trixnity.olm.freeAfter
import arrow.core.flatMap as flatMapResult

private val log = KotlinLogging.logger {}

interface KeyService {
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
            val key = recoveryKeyFromPassphrase(passphrase, passphraseInfo)
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
    fun getTrustLevel(
        userId: UserId,
        deviceId: String,
    ): Flow<DeviceTrustLevel>

    /**
     * @return the trust level of a device or null, if the timeline event is not a megolm encrypted event.
     */
    fun getTrustLevel(
        roomId: RoomId,
        eventId: EventId,
    ): Flow<DeviceTrustLevel?>

    /**
     * @return the trust level of a user. This will only be present, if the requested user has cross signing enabled.
     */
    fun getTrustLevel(
        userId: UserId,
    ): Flow<UserTrustLevel>

    fun getDeviceKeys(
        userId: UserId,
    ): Flow<List<DeviceKeys>?>

    fun getCrossSigningKeys(
        userId: UserId,
    ): Flow<List<CrossSigningKeys>?>
}

class KeyServiceImpl(
    private val userInfo: UserInfo,
    private val keyStore: KeyStore,
    private val olmCryptoStore: OlmCryptoStore,
    private val globalAccountDataStore: GlobalAccountDataStore,
    private val roomService: RoomService,
    private val signService: SignService,
    private val keyBackupService: KeyBackupService,
    private val keyTrustService: KeyTrustService,
    private val api: MatrixClientServerApiClient,
) : KeyService {

    private val _bootstrapRunning = MutableStateFlow(false)
    override val bootstrapRunning = _bootstrapRunning.asStateFlow()

    override suspend fun bootstrapCrossSigning(
        recoveryKey: ByteArray,
        secretKeyEventContentGenerator: suspend () -> SecretKeyEventContent
    ): KeyService.BootstrapCrossSigning {
        log.debug { "bootstrap cross signing" }
        _bootstrapRunning.value = true

        val keyId = generateSequence {
            val alphabet = 'a'..'z'
            generateSequence { alphabet.random() }.take(24).joinToString("")
        }.first { globalAccountDataStore.get<SecretKeyEventContent>(key = it).first() == null }
        val secretKeyEventContent = secretKeyEventContentGenerator()
        return KeyService.BootstrapCrossSigning(
            recoveryKey = encodeRecoveryKey(recoveryKey),
            result = api.users.setAccountData(secretKeyEventContent, userInfo.userId, keyId)
                .flatMapResult { api.users.setAccountData(DefaultSecretKeyEventContent(keyId), userInfo.userId) }
                .flatMapResult {
                    val (masterSigningPrivateKey, masterSigningPublicKey) =
                        freeAfter(OlmPkSigning.create(null)) { it.privateKey to it.publicKey }
                    val masterSigningKey = signService.sign(
                        CrossSigningKeys(
                            userId = userInfo.userId,
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
                            userId = userInfo.userId,
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
                            userId = userInfo.userId,
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
                    keyStore.secrets.update {
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
                    api.users.setAccountData(encryptedMasterSigningKey, userInfo.userId)
                        .flatMapResult { api.users.setAccountData(encryptedUserSigningKey, userInfo.userId) }
                        .flatMapResult { api.users.setAccountData(encryptedSelfSigningKey, userInfo.userId) }
                        .flatMapResult {
                            keyBackupService.bootstrapRoomKeyBackup(
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
                }.mapCatching { uiaFlow ->
                    uiaFlow.injectOnSuccessIntoUIA {
                        keyStore.updateOutdatedKeys { oldOutdatedKeys -> oldOutdatedKeys + userInfo.userId }
                        log.debug { "wait for outdated keys" }
                        keyStore.waitForUpdateOutdatedKey(userInfo.userId)
                        val masterKey =
                            keyStore.getCrossSigningKey(userInfo.userId, MasterKey)?.value?.signed?.get<Ed25519Key>()
                        val ownDeviceKey =
                            keyStore.getDeviceKey(userInfo.userId, userInfo.deviceId).first()?.value?.get<Ed25519Key>()

                        keyTrustService.trustAndSignKeys(setOfNotNull(masterKey, ownDeviceKey), userInfo.userId)
                        log.debug { "wait for own device keys to be marked as cross signed and verified" }
                        keyStore.getDeviceKey(userInfo.userId, userInfo.deviceId)
                            .mapNotNull { it?.trustLevel }
                            .first { it is CrossSigned && it.verified }
                        _bootstrapRunning.value = false
                        log.debug { "finished bootstrapping" }
                    }
                }
        )
    }

    override suspend fun bootstrapCrossSigningFromPassphrase(
        passphrase: String,
        secretKeyEventContentGenerator: suspend () -> Pair<ByteArray, SecretKeyEventContent>
    ): KeyService.BootstrapCrossSigning {
        val secretKeyEventContent = secretKeyEventContentGenerator()
        return bootstrapCrossSigning(secretKeyEventContent.first) { secretKeyEventContent.second }
    }

    override fun getTrustLevel(
        userId: UserId,
        deviceId: String,
    ): Flow<DeviceTrustLevel> {
        return keyStore.getDeviceKey(userId, deviceId).map { deviceKeys ->
            when (val trustLevel = deviceKeys?.trustLevel) {
                is Valid -> DeviceTrustLevel.Valid(trustLevel.verified)
                is CrossSigned -> DeviceTrustLevel.CrossSigned(trustLevel.verified)
                is NotCrossSigned -> DeviceTrustLevel.NotCrossSigned
                is Blocked -> DeviceTrustLevel.Blocked
                is Invalid -> DeviceTrustLevel.Invalid(trustLevel.reason)
                is NotAllDeviceKeysCrossSigned, null -> DeviceTrustLevel.Invalid("could not determine trust level of device key: $deviceKeys")
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getTrustLevel(
        roomId: RoomId,
        eventId: EventId,
    ): Flow<DeviceTrustLevel?> =
        roomService.getTimelineEvent(roomId, eventId).flatMapLatest { timelineEvent ->
            val event = timelineEvent?.event
            val content = event?.content
            if (event is Event.MessageEvent && content is EncryptedEventContent.MegolmEncryptedEventContent) {
                combine(
                    olmCryptoStore.getInboundMegolmSession(content.sessionId, event.roomId),
                    keyStore.getDeviceKeys(event.sender)
                ) { megolmSession, deviceKeys ->
                    when {
                        megolmSession == null || deviceKeys == null -> flowOf(DeviceTrustLevel.Invalid("could not find session or device key"))
                        megolmSession.isTrusted.not() -> flowOf(DeviceTrustLevel.NotTrusted)
                        else -> {
                            val deviceId =
                                deviceKeys.values.find { it.value.signed.keys.keys.contains(megolmSession.senderKey) }
                                    ?.value?.signed?.deviceId
                            if (deviceId != null) getTrustLevel(event.sender, deviceId)
                            else flowOf(DeviceTrustLevel.Invalid("could not find device id"))
                        }
                    }
                }.flatMapLatest { it }
            } else flowOf(null)
        }

    override fun getTrustLevel(
        userId: UserId,
    ): Flow<UserTrustLevel> {
        return keyStore.getCrossSigningKeys(userId)
            .map { keys -> keys?.firstOrNull { it.value.signed.usage.contains(MasterKey) } }
            .map { crossSigningKeys ->
                when (val trustLevel = crossSigningKeys?.trustLevel) {
                    is Valid -> UserTrustLevel.CrossSigned(trustLevel.verified)
                    is CrossSigned -> UserTrustLevel.CrossSigned(trustLevel.verified)
                    is NotAllDeviceKeysCrossSigned -> UserTrustLevel.NotAllDevicesCrossSigned(trustLevel.verified)
                    is Blocked -> UserTrustLevel.Blocked
                    is Invalid -> UserTrustLevel.Invalid(trustLevel.reason)
                    is NotCrossSigned -> UserTrustLevel.Invalid("could not determine trust level of cross signing key: $crossSigningKeys")
                    null -> UserTrustLevel.Unknown
                }
            }
    }

    override fun getDeviceKeys(
        userId: UserId,
    ): Flow<List<DeviceKeys>?> {
        return keyStore.getDeviceKeys(userId).map {
            it?.values?.map { storedKeys -> storedKeys.value.signed }
        }
    }

    override fun getCrossSigningKeys(
        userId: UserId,
    ): Flow<List<CrossSigningKeys>?> {
        return keyStore.getCrossSigningKeys(userId).map {
            it?.map { storedKeys -> storedKeys.value.signed }
        }
    }
}