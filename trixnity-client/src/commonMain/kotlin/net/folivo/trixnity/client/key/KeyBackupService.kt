package net.folivo.trixnity.client.key

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.flatMap
import net.folivo.trixnity.client.store.AccountStore
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.client.store.OlmCryptoStore
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.client.utils.retryLoop
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.keys.GetRoomKeysBackupVersionResponse
import net.folivo.trixnity.clientserverapi.model.keys.SetRoomKeyBackupVersionRequest
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.m.MegolmBackupV1EventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.RoomKeyBackupSessionData.EncryptedRoomKeyBackupV1SessionData
import net.folivo.trixnity.core.model.keys.RoomKeyBackupSessionData.EncryptedRoomKeyBackupV1SessionData.RoomKeyBackupV1SessionData
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.crypto.key.encryptSecret
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession
import net.folivo.trixnity.crypto.sign.SignService
import net.folivo.trixnity.crypto.sign.SignWith
import net.folivo.trixnity.crypto.sign.signatures
import net.folivo.trixnity.olm.*
import net.folivo.trixnity.utils.retry
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger("net.folivo.trixnity.client.key.KeyBackupService")

interface KeyBackupService {
    /**
     * This is the active key backup version.
     * Is null, when the backup algorithm is not supported or there is no existing backup.
     */
    val version: StateFlow<GetRoomKeysBackupVersionResponse.V1?>

    suspend fun loadMegolmSession(
        roomId: RoomId,
        sessionId: String,
    )

    suspend fun keyBackupCanBeTrusted(keyBackupVersion: GetRoomKeysBackupVersionResponse, privateKey: String): Boolean

    suspend fun bootstrapRoomKeyBackup(
        key: ByteArray,
        keyId: String,
        masterSigningPrivateKey: String,
        masterSigningPublicKey: String
    ): Result<Unit>
}

class KeyBackupServiceImpl(
    userInfo: UserInfo,
    private val accountStore: AccountStore,
    private val olmCryptoStore: OlmCryptoStore,
    private val keyStore: KeyStore,
    private val api: MatrixClientServerApiClient,
    private val signService: SignService,
    private val currentSyncState: CurrentSyncState,
    private val scope: CoroutineScope,
) : KeyBackupService, EventHandler {
    private val ownUserId = userInfo.userId
    private val ownDeviceId = userInfo.deviceId
    private val currentBackupVersion = MutableStateFlow<GetRoomKeysBackupVersionResponse.V1?>(null)

    /**
     * This is the active key backup version.
     * Is null, when the backup algorithm is not supported or there is no existing backup.
     */
    override val version = currentBackupVersion.asStateFlow()

    override fun startInCoroutineScope(scope: CoroutineScope) {
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        scope.launch(start = CoroutineStart.UNDISPATCHED) { setAndSignNewKeyBackupVersion() }
        scope.launch(start = CoroutineStart.UNDISPATCHED) { uploadRoomKeyBackup() }
    }

    internal suspend fun setAndSignNewKeyBackupVersion() {
        currentSyncState.retryLoop(
            onError = { error, delay -> log.warn(error) { "failed get (and sign) current room key version, try again in $delay" } },
        ) {
            keyStore.getSecretsFlow().mapNotNull { it[SecretType.M_MEGOLM_BACKUP_V1] }
                .distinctUntilChanged()
                // TODO should use the version from secret, when MSC2474 is merged
                .collectLatest { updateKeyBackupVersion(it.decryptedPrivateKey) }
        }
    }

    private suspend fun updateKeyBackupVersion(privateKey: String?) {
        log.debug { "check key backup version" }
        val currentVersion = api.key.getRoomKeysVersion().getOrThrow().let { currentVersion ->
            if (currentVersion is GetRoomKeysBackupVersionResponse.V1) {
                val deviceSignature =
                    signService.signatures(currentVersion.authData)[ownUserId]?.find { it.id == ownDeviceId }
                if (privateKey != null && keyBackupCanBeTrusted(currentVersion, privateKey)) {
                    if (deviceSignature != null &&
                        currentVersion.authData.signatures[ownUserId]?.none { it == deviceSignature } == true
                    ) {
                        log.info { "sign key backup" }
                        api.key.setRoomKeysVersion(
                            SetRoomKeyBackupVersionRequest.V1(
                                authData = with(currentVersion.authData) {
                                    val ownUsersSignatures = signatures[ownUserId].orEmpty()
                                        .filterNot { it.id == ownDeviceId } + deviceSignature
                                    copy(signatures = signatures + (ownUserId to Keys(ownUsersSignatures.toSet())))
                                },
                                version = currentVersion.version
                            )
                        ).getOrThrow()
                    }
                    currentVersion
                } else {
                    // TODO should we mark all known keys as not backed up?
                    log.info { "reset key backup and remove own signature from it" }
                    // when the private key does not match it's likely, that the key backup has been changed
                    if (currentVersion.authData.signatures[ownUserId]?.any { it.id == ownDeviceId } == true)
                        api.key.setRoomKeysVersion(
                            SetRoomKeyBackupVersionRequest.V1(
                                authData = with(currentVersion.authData) {
                                    val ownUsersSignatures =
                                        signatures[ownUserId].orEmpty()
                                            .filterNot { it.id == ownDeviceId }
                                            .toSet()
                                    copy(signatures = signatures + (ownUserId to Keys(ownUsersSignatures)))
                                },
                                version = currentVersion.version
                            )
                        ).getOrThrow()
                    keyStore.updateSecrets { it - SecretType.M_MEGOLM_BACKUP_V1 }
                    null
                }
            } else {
                log.warn { "unsupported key backup version" }
                null
            }
        }
        currentBackupVersion.value = currentVersion
    }


    private val currentlyLoadingMegolmSessions = MutableStateFlow<Set<Pair<RoomId, String>>>(setOf())

    override suspend fun loadMegolmSession(
        roomId: RoomId,
        sessionId: String,
    ): Unit = coroutineScope {
        val runningKey = Pair(roomId, sessionId)
        if (currentlyLoadingMegolmSessions.getAndUpdate { it + runningKey }.contains(runningKey).not()) {
            scope.launch {
                currentCoroutineContext().job.invokeOnCompletion {
                    currentlyLoadingMegolmSessions.update { it - runningKey }
                }
                val version = version.filterNotNull().first().version
                retry(
                    scheduleBase = 1.seconds,
                    scheduleLimit = 6.hours,
                    onError = { error, delay ->
                        if (error is MatrixServerException
                            && error.statusCode == HttpStatusCode.NotFound
                            && error.errorResponse is ErrorResponse.NotFound
                        ) log.trace(error) { "megolm session from key backup not found on server, try again in $delay" }
                        else log.warn(error) { "failed load megolm session from key backup, try again in $delay" }
                    },
                ) {
                    log.debug { "try to find key backup for roomId=$roomId, sessionId=$sessionId, version=$version" }
                    val encryptedSessionData = api.key.getRoomKeys(version, roomId, sessionId).getOrThrow().sessionData
                    require(encryptedSessionData is EncryptedRoomKeyBackupV1SessionData)
                    val privateKey = keyStore.getSecrets()[SecretType.M_MEGOLM_BACKUP_V1]?.decryptedPrivateKey
                    val decryptedJson = freeAfter(OlmPkDecryption.create(privateKey)) {
                        it.decrypt(
                            with(encryptedSessionData) {
                                OlmPkMessage(
                                    cipherText = ciphertext,
                                    mac = mac,
                                    ephemeralKey = ephemeral
                                )
                            }
                        )
                    }
                    val data = api.json.decodeFromString<RoomKeyBackupV1SessionData>(decryptedJson)
                    val (firstKnownIndex, pickledSession) =
                        freeAfter(OlmInboundGroupSession.import(data.sessionKey)) {
                            it.firstKnownIndex to it.pickle(accountStore.getAccount()?.olmPickleKey)
                        }
                    val senderSigningKey =
                        data.senderClaimedKeys.filterIsInstance<Key.Ed25519Key>().firstOrNull()
                            ?: throw IllegalArgumentException("sender claimed key should not be empty")
                    olmCryptoStore.updateInboundMegolmSession(sessionId, roomId) {
                        if (it != null && it.firstKnownIndex <= firstKnownIndex) it
                        else StoredInboundMegolmSession(
                            senderKey = data.senderKey,
                            sessionId = sessionId,
                            roomId = roomId,
                            firstKnownIndex = firstKnownIndex,
                            isTrusted = false, // because it comes from backup
                            hasBeenBackedUp = true, // because it comes from backup
                            senderSigningKey = senderSigningKey.value,
                            forwardingCurve25519KeyChain = data.forwardingKeyChain,
                            pickled = pickledSession
                        )
                    }
                }
                log.debug { "found key backup for roomId=$roomId, sessionId=$sessionId" }
            }
        }
        currentlyLoadingMegolmSessions.first { it.contains(runningKey).not() }
    }

    override suspend fun keyBackupCanBeTrusted(
        keyBackupVersion: GetRoomKeysBackupVersionResponse,
        privateKey: String,
    ): Boolean {
        val generatedPublicKey = try {
            freeAfter(OlmPkDecryption.create(privateKey)) { it.publicKey }
        } catch (error: Exception) {
            log.warn(error) { "could not generate public key from private backup key" }
            return false
        }
        if (keyBackupVersion !is GetRoomKeysBackupVersionResponse.V1) {
            log.warn { "current room key backup version does not match v1 or there was no backup" }
            return false
        }
        val originalPublicKey = keyBackupVersion.authData.publicKey.value
        if (originalPublicKey != generatedPublicKey) {
            log.warn { "key backup private key does not match public key (expected: $originalPublicKey was: $generatedPublicKey" }
            return false
        }
        return true
    }

    @OptIn(FlowPreview::class)
    internal suspend fun uploadRoomKeyBackup() {
        currentSyncState.retryLoop(
            onError = { error, delay -> log.warn(error) { "failed upload room key backup, try again in $delay" } },
        ) {
            olmCryptoStore.notBackedUpInboundMegolmSessions.debounce(1.seconds)
                .onEach { notBackedUpInboundMegolmSessions ->
                    val version = version.value
                    if (version != null && notBackedUpInboundMegolmSessions.isNotEmpty()) {
                        log.debug { "upload room keys to key backup" }
                        api.key.setRoomKeys(
                            version.version, RoomsKeyBackup(
                                notBackedUpInboundMegolmSessions.values.groupBy { it.roomId }
                                    .mapValues { roomEntries ->
                                        RoomKeyBackup(roomEntries.value.associate { session ->
                                            val encryptedRoomKeyBackupV1SessionData =
                                                freeAfter(OlmPkEncryption.create(version.authData.publicKey.value)) { pke ->
                                                    val sessionKey = freeAfter(
                                                        OlmInboundGroupSession.unpickle(
                                                            accountStore.getAccount()?.olmPickleKey,
                                                            session.pickled
                                                        )
                                                    ) { it.export(it.firstKnownIndex) }
                                                    pke.encrypt(
                                                        api.json.encodeToString(
                                                            RoomKeyBackupV1SessionData(
                                                                session.senderKey,
                                                                session.forwardingCurve25519KeyChain,
                                                                Keys(Key.Ed25519Key(null, session.senderSigningKey)),
                                                                sessionKey
                                                            )
                                                        )
                                                    ).run {
                                                        EncryptedRoomKeyBackupV1SessionData(
                                                            ciphertext = cipherText,
                                                            mac = mac,
                                                            ephemeral = ephemeralKey
                                                        )
                                                    }
                                                }
                                            session.sessionId to RoomKeyBackupData(
                                                firstMessageIndex = session.firstKnownIndex,
                                                forwardedCount = session.forwardingCurve25519KeyChain.size,
                                                isVerified = session.isTrusted,
                                                sessionData = encryptedRoomKeyBackupV1SessionData
                                            )
                                        })
                                    }
                            )).onFailure {
                            if (it is MatrixServerException) {
                                val errorResponse = it.errorResponse
                                if (errorResponse is ErrorResponse.WrongRoomKeysVersion) {
                                    log.info { "key backup version is outdated" }
                                    updateKeyBackupVersion(keyStore.getSecrets()[SecretType.M_MEGOLM_BACKUP_V1]?.decryptedPrivateKey)
                                }
                            }
                        }.getOrThrow()
                        notBackedUpInboundMegolmSessions.values.forEach {
                            olmCryptoStore.updateInboundMegolmSession(it.sessionId, it.roomId) { session ->
                                session?.copy(hasBeenBackedUp = true)
                            }
                        }
                    }
                }.collect()
        }
    }

    override suspend fun bootstrapRoomKeyBackup(
        key: ByteArray,
        keyId: String,
        masterSigningPrivateKey: String,
        masterSigningPublicKey: String,
    ): Result<Unit> {
        val (keyBackupPrivateKey, keyBackupPublicKey) = freeAfter(OlmPkDecryption.create(null)) { it.privateKey to it.publicKey }
        return api.key.setRoomKeysVersion(
            SetRoomKeyBackupVersionRequest.V1(
                authData = with(
                    RoomKeyBackupAuthData.RoomKeyBackupV1AuthData(KeyValue.Curve25519KeyValue(keyBackupPublicKey))
                ) {
                    val ownDeviceSignature = signService.signatures(this)[ownUserId]
                        ?.firstOrNull()
                    val ownUsersSignature =
                        signService.signatures(
                            this,
                            SignWith.KeyPair(masterSigningPrivateKey, masterSigningPublicKey)
                        )[ownUserId]
                            ?.firstOrNull()
                    requireNotNull(ownUsersSignature)
                    requireNotNull(ownDeviceSignature)
                    copy(signatures = signatures + (ownUserId to keysOf(ownDeviceSignature, ownUsersSignature)))
                },
                version = null // create new version
            )
        ).flatMap {
            val encryptedBackupKey = MegolmBackupV1EventContent(
                encryptSecret(key, keyId, SecretType.M_MEGOLM_BACKUP_V1.id, keyBackupPrivateKey, api.json)
            )
            api.user.setAccountData(encryptedBackupKey, ownUserId).also {
                keyStore.updateSecrets {
                    it + (SecretType.M_MEGOLM_BACKUP_V1 to StoredSecret(
                        GlobalAccountDataEvent(encryptedBackupKey),
                        keyBackupPrivateKey
                    ))
                }
            }
        }
    }
}