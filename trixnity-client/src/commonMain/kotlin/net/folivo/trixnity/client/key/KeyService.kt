package net.folivo.trixnity.client.key

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.flatMap
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.KeySignatureTrustLevel.CrossSigned
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.UIA
import net.folivo.trixnity.clientserverapi.client.injectOnSuccessIntoUIA
import net.folivo.trixnity.core.MSC3814
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import net.folivo.trixnity.core.model.events.m.DehydratedDeviceEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.MasterKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key
import net.folivo.trixnity.core.model.keys.CrossSigningKeys
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage.*
import net.folivo.trixnity.core.model.keys.DeviceKeys
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.crypto.core.SecureRandom
import net.folivo.trixnity.crypto.core.createAesHmacSha2MacFromKey
import net.folivo.trixnity.crypto.key.*
import net.folivo.trixnity.crypto.key.DeviceTrustLevel
import net.folivo.trixnity.crypto.key.UserTrustLevel
import net.folivo.trixnity.crypto.sign.SignService
import net.folivo.trixnity.crypto.sign.SignWith
import net.folivo.trixnity.crypto.sign.sign
import net.folivo.trixnity.olm.OlmPkSigning
import net.folivo.trixnity.olm.freeAfter
import net.folivo.trixnity.utils.encodeUnpaddedBase64
import kotlin.random.Random

private val log = KotlinLogging.logger("net.folivo.trixnity.client.key.KeyService")

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
        recoveryKey: ByteArray,
        secretKeyEventContent: SecretKeyEventContent,
    ): BootstrapCrossSigning

    /**
     * This allows you to bootstrap cross signing. Be aware, that this could override an existing cross signing setup of
     * the account. Be aware, that this also creates a new key backup, which could replace an existing key backup.
     */
    suspend fun bootstrapCrossSigning(): BootstrapCrossSigning

    /**
     * This allows you to bootstrap cross signing. Be aware, that this could override an existing cross signing setup of
     * the account. Be aware, that this also creates a new key backup, which could replace an existing key backup.
     */
    suspend fun bootstrapCrossSigningFromPassphrase(passphrase: String): BootstrapCrossSigning

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
    private val matrixClientConfiguration: MatrixClientConfiguration,
) : KeyService {

    private val _bootstrapRunning = MutableStateFlow(false)
    override val bootstrapRunning = _bootstrapRunning.asStateFlow()

    override suspend fun bootstrapCrossSigning(
        recoveryKey: ByteArray,
        secretKeyEventContent: SecretKeyEventContent,
    ): KeyService.BootstrapCrossSigning {
        log.debug { "bootstrap cross signing" }
        _bootstrapRunning.value = true

        val keyId = generateSequence {
            val alphabet = 'a'..'z'
            generateSequence { alphabet.random() }.take(24).joinToString("")
        }.first { globalAccountDataStore.get<SecretKeyEventContent>(key = it).first() == null }
        return KeyService.BootstrapCrossSigning(
            recoveryKey = encodeRecoveryKey(recoveryKey),
            result = api.user.setAccountData(secretKeyEventContent, userInfo.userId, keyId)
                .flatMap { api.user.setAccountData(DefaultSecretKeyEventContent(keyId), userInfo.userId) }
                .flatMap {
                    val (masterSigningPrivateKey, masterSigningPublicKey) =
                        freeAfter(OlmPkSigning.create()) { it.privateKey to it.publicKey }
                    val masterSigningKey = signService.sign(
                        CrossSigningKeys(
                            userId = userInfo.userId,
                            usage = setOf(MasterKey),
                            keys = keysOf(Ed25519Key(masterSigningPublicKey, masterSigningPublicKey))
                        ),
                        signWith = SignWith.KeyPair(
                            privateKey = masterSigningPrivateKey,
                            publicKey = masterSigningPublicKey
                        )
                    )
                    val (selfSigningPrivateKey, selfSigningPublicKey) =
                        freeAfter(OlmPkSigning.create()) { it.privateKey to it.publicKey }
                    val selfSigningKey = signService.sign(
                        CrossSigningKeys(
                            userId = userInfo.userId,
                            usage = setOf(SelfSigningKey),
                            keys = keysOf(Ed25519Key(selfSigningPublicKey, selfSigningPublicKey))
                        ),
                        signWith = SignWith.KeyPair(
                            privateKey = masterSigningPrivateKey,
                            publicKey = masterSigningPublicKey
                        )
                    )
                    val (userSigningPrivateKey, userSigningPublicKey) =
                        freeAfter(OlmPkSigning.create()) { it.privateKey to it.publicKey }
                    val userSigningKey = signService.sign(
                        CrossSigningKeys(
                            userId = userInfo.userId,
                            usage = setOf(UserSigningKey),
                            keys = keysOf(Ed25519Key(userSigningPublicKey, userSigningPublicKey))
                        ),
                        signWith = SignWith.KeyPair(
                            privateKey = masterSigningPrivateKey,
                            publicKey = masterSigningPublicKey
                        )
                    )

                    val masterKeyEventContent = MasterKeyEventContent(
                        encryptSecret(
                            key = recoveryKey,
                            keyId = keyId,
                            secretName = SecretType.M_CROSS_SIGNING_MASTER.id,
                            secret = masterSigningPrivateKey,
                            json = api.json
                        )
                    )
                    api.user.setAccountData(masterKeyEventContent, userInfo.userId)
                        .flatMap {
                            val userSigningKeyEventContent = UserSigningKeyEventContent(
                                encryptSecret(
                                    key = recoveryKey,
                                    keyId = keyId,
                                    secretName = SecretType.M_CROSS_SIGNING_USER_SIGNING.id,
                                    secret = userSigningPrivateKey,
                                    json = api.json
                                )
                            )
                            api.user.setAccountData(userSigningKeyEventContent, userInfo.userId).also {
                                keyStore.updateSecrets {
                                    it + mapOf(
                                        SecretType.M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                                            GlobalAccountDataEvent(userSigningKeyEventContent),
                                            userSigningPrivateKey
                                        ),
                                    )
                                }
                            }
                        }
                        .flatMap {
                            val selfSigningKeyEventContent = SelfSigningKeyEventContent(
                                encryptSecret(
                                    key = recoveryKey,
                                    keyId = keyId,
                                    secretName = SecretType.M_CROSS_SIGNING_SELF_SIGNING.id,
                                    secret = selfSigningPrivateKey,
                                    json = api.json
                                )
                            )
                            api.user.setAccountData(selfSigningKeyEventContent, userInfo.userId).also {
                                keyStore.updateSecrets {
                                    it + mapOf(
                                        SecretType.M_CROSS_SIGNING_SELF_SIGNING to StoredSecret(
                                            GlobalAccountDataEvent(selfSigningKeyEventContent),
                                            selfSigningPrivateKey
                                        ),
                                    )
                                }
                            }
                        }
                        .flatMap @OptIn(MSC3814::class) {
                            if (matrixClientConfiguration.experimentalFeatures.enableMSC3814) {
                                val dehydratedDeviceKey =
                                    Random.nextBytes(32).encodeUnpaddedBase64()
                                val dehydratedDeviceEventContent =
                                    DehydratedDeviceEventContent(
                                        encryptSecret(
                                            key = recoveryKey,
                                            keyId = keyId,
                                            secretName = SecretType.M_DEHYDRATED_DEVICE.id,
                                            secret = dehydratedDeviceKey,
                                            json = api.json
                                        )
                                    )
                                api.user.setAccountData(dehydratedDeviceEventContent, userInfo.userId).also {
                                    keyStore.updateSecrets {
                                        it + mapOf(
                                            SecretType.M_DEHYDRATED_DEVICE to StoredSecret(
                                                GlobalAccountDataEvent(dehydratedDeviceEventContent),
                                                dehydratedDeviceKey
                                            ),
                                        )
                                    }
                                }
                            } else Result.success(Unit)
                        }
                        .flatMap {
                            keyBackupService.bootstrapRoomKeyBackup(
                                recoveryKey,
                                keyId,
                                masterSigningPrivateKey,
                                masterSigningPublicKey
                            )
                        }
                        .flatMap {
                            api.key.setCrossSigningKeys(
                                masterKey = masterSigningKey,
                                selfSigningKey = selfSigningKey,
                                userSigningKey = userSigningKey
                            )
                        }
                }.mapCatching { uiaFlow ->
                    uiaFlow.injectOnSuccessIntoUIA {
                        keyStore.updateOutdatedKeys { oldOutdatedKeys -> oldOutdatedKeys + userInfo.userId }
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

    override suspend fun bootstrapCrossSigning(): KeyService.BootstrapCrossSigning {
        val recoveryKey = SecureRandom.nextBytes(32)
        val iv = SecureRandom.nextBytes(16)
        val secretKeyEventContent = AesHmacSha2Key(
            iv = iv.encodeBase64(),
            mac = createAesHmacSha2MacFromKey(recoveryKey, iv)
        )

        return bootstrapCrossSigning(recoveryKey, secretKeyEventContent)
    }

    override suspend fun bootstrapCrossSigningFromPassphrase(
        passphrase: String,
    ): KeyService.BootstrapCrossSigning {
        val passphraseInfo = AesHmacSha2Key.SecretStorageKeyPassphrase.Pbkdf2(
            salt = SecureRandom.nextBytes(32).encodeBase64(),
            iterations = 210_000,
            bits = 32 * 8
        )
        val iv = SecureRandom.nextBytes(16)
        val recoveryKey = recoveryKeyFromPassphrase(passphrase, passphraseInfo)
        val secretKeyEventContent = AesHmacSha2Key(
            passphrase = passphraseInfo,
            iv = iv.encodeBase64(),
            mac = createAesHmacSha2MacFromKey(key = recoveryKey, iv = iv)
        )
        return bootstrapCrossSigning(recoveryKey, secretKeyEventContent)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getTrustLevel(
        roomId: RoomId,
        eventId: EventId,
    ): Flow<DeviceTrustLevel?> =
        roomService.getTimelineEvent(roomId, eventId).flatMapLatest { timelineEvent ->
            val event = timelineEvent?.event
            val content = event?.content
            if (event is MessageEvent && content is MegolmEncryptedMessageEventContent) {
                combine(
                    olmCryptoStore.getInboundMegolmSession(content.sessionId, event.roomId),
                    keyStore.getDeviceKeys(event.sender)
                ) { megolmSession, deviceKeys ->
                    when {
                        megolmSession == null || deviceKeys == null -> flowOf(DeviceTrustLevel.Invalid("could not find session or device key"))
                        megolmSession.isTrusted.not() -> flowOf(DeviceTrustLevel.NotTrusted)
                        else -> {
                            val deviceId =
                                deviceKeys.values.find { it.value.signed.keys.keys.any { it.value == megolmSession.senderKey } }
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
        deviceId: String,
    ): Flow<DeviceTrustLevel> {
        return keyStore.getDeviceKey(userId, deviceId).map { it?.trustLevel.toDeviceTrustLevel() }
    }

    override fun getTrustLevel(
        userId: UserId,
    ): Flow<UserTrustLevel> {
        return keyStore.getCrossSigningKeys(userId)
            .map { keys -> keys?.firstOrNull { it.value.signed.usage.contains(MasterKey) } }
            .map { it?.trustLevel.toUserTrustLevel() }
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