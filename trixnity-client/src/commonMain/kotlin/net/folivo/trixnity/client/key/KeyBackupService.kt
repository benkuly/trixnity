package net.folivo.trixnity.client.key

import arrow.core.flatMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import mu.KotlinLogging
import net.folivo.trixnity.client.crypto.IOlmSignService
import net.folivo.trixnity.client.crypto.IOlmSignService.SignWith.Custom
import net.folivo.trixnity.client.crypto.signatures
import net.folivo.trixnity.client.retryInfiniteWhenSyncIs
import net.folivo.trixnity.client.retryWhen
import net.folivo.trixnity.client.store.AllowedSecretType.M_MEGOLM_BACKUP_V1
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoredInboundMegolmSession
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.clientserverapi.model.keys.GetRoomKeysBackupVersionResponse
import net.folivo.trixnity.clientserverapi.model.keys.SetRoomKeyBackupVersionRequest
import net.folivo.trixnity.core.ErrorResponse
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.MegolmBackupV1EventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.RoomKeyBackupSessionData.EncryptedRoomKeyBackupV1SessionData
import net.folivo.trixnity.core.model.keys.RoomKeyBackupSessionData.EncryptedRoomKeyBackupV1SessionData.RoomKeyBackupV1SessionData
import net.folivo.trixnity.olm.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

interface IKeyBackupService {
    /**
     * This is the active key backup version.
     * Is null, when the backup algorithm is not supported or there is no existing backup.
     */
    val version: StateFlow<GetRoomKeysBackupVersionResponse.V1?>

    data class LoadMegolmSession(
        val roomId: RoomId,
        val sessionId: String,
        val senderKey: Key.Curve25519Key,
    )

    fun loadMegolmSession(
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

class KeyBackupService(
    private val olmPickleKey: String,
    private val ownUserId: UserId,
    private val ownDeviceId: String,
    private val store: Store,
    private val api: MatrixClientServerApiClient,
    private val olmSign: IOlmSignService,
    private val currentSyncState: StateFlow<SyncState>,
    private val scope: CoroutineScope,
) : IKeyBackupService {
    private val currentBackupVersion = MutableStateFlow<GetRoomKeysBackupVersionResponse.V1?>(null)

    /**
     * This is the active key backup version.
     * Is null, when the backup algorithm is not supported or there is no existing backup.
     */
    override val version = currentBackupVersion.asStateFlow()

    init {
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        scope.launch(start = CoroutineStart.UNDISPATCHED) { setAndSignNewKeyBackupVersion() }
        scope.launch(start = CoroutineStart.UNDISPATCHED) { uploadRoomKeyBackup() }
    }

    internal suspend fun setAndSignNewKeyBackupVersion() {
        currentSyncState.retryInfiniteWhenSyncIs(
            SyncState.RUNNING,
            onError = { log.warn(it) { "failed get (and sign) current room key version" } },
            onCancel = { log.info { "stop get current room key version, because job was cancelled" } },
        ) {
            store.keys.secrets.mapNotNull { it[M_MEGOLM_BACKUP_V1] }
                .distinctUntilChanged()
                // TODO should use the version from secret, when MSC2474 is merged
                .collectLatest { updateKeyBackupVersion(it.decryptedPrivateKey) }
        }
    }

    private suspend fun updateKeyBackupVersion(privateKey: String?) {
        log.debug { "check key backup version" }
        val currentVersion = api.keys.getRoomKeysVersion().getOrThrow().let { currentVersion ->
            if (currentVersion is GetRoomKeysBackupVersionResponse.V1) {
                val deviceSignature =
                    olmSign.signatures(currentVersion.authData)[ownUserId]?.find { it.keyId == ownDeviceId }
                if (privateKey != null && keyBackupCanBeTrusted(currentVersion, privateKey)) {
                    if (deviceSignature != null &&
                        currentVersion.authData.signatures[ownUserId]?.none { it == deviceSignature } == true
                    ) {
                        log.info { "sign key backup" }
                        api.keys.setRoomKeysVersion(
                            SetRoomKeyBackupVersionRequest.V1(
                                authData = with(currentVersion.authData) {
                                    val ownUsersSignatures = signatures[ownUserId].orEmpty()
                                        .filterNot { it.keyId == ownDeviceId } + deviceSignature
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
                    if (currentVersion.authData.signatures[ownUserId]?.any { it.keyId == ownDeviceId } == true)
                        api.keys.setRoomKeysVersion(
                            SetRoomKeyBackupVersionRequest.V1(
                                authData = with(currentVersion.authData) {
                                    val ownUsersSignatures =
                                        signatures[ownUserId].orEmpty()
                                            .filterNot { it.keyId == ownDeviceId }
                                            .toSet()
                                    copy(signatures = signatures + (ownUserId to Keys(ownUsersSignatures)))
                                },
                                version = currentVersion.version
                            )
                        ).getOrThrow()
                    store.keys.secrets.update { it - M_MEGOLM_BACKUP_V1 }
                    null
                }
            } else null
        }
        currentBackupVersion.value = currentVersion
    }


    private val currentlyLoadingMegolmSessions = MutableStateFlow<Set<Pair<RoomId, String>>>(setOf())

    override fun loadMegolmSession(
        roomId: RoomId,
        sessionId: String,
    ) {
        val runningKey = Pair(roomId, sessionId)
        if (currentlyLoadingMegolmSessions.getAndUpdate { it + runningKey }.contains(runningKey).not()) {
            scope.launch {
                retryWhen(
                    combine(version, currentSyncState) { currentVersion, currentSyncState ->
                        currentVersion != null && currentSyncState == SyncState.RUNNING
                    },
                    scheduleBase = 1.seconds,
                    scheduleLimit = 6.hours,
                    onError = { log.warn(it) { "failed load megolm session from key backup" } },
                    onCancel = { log.debug { "stop load megolm session from key backup, because job was cancelled" } },
                ) {
                    val version = version.value?.version
                    if (version != null) {
                        log.debug { "try to find key backup for roomId=$roomId, sessionId=$sessionId, version=$version" }
                        val encryptedSessionData =
                            api.keys.getRoomKeys(version, roomId, sessionId).getOrThrow().sessionData
                        require(encryptedSessionData is EncryptedRoomKeyBackupV1SessionData)
                        val privateKey = store.keys.secrets.value[M_MEGOLM_BACKUP_V1]?.decryptedPrivateKey
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
                                it.firstKnownIndex to it.pickle(olmPickleKey)
                            }
                        val senderSigningKey = Key.Ed25519Key(
                            null,
                            data.senderClaimedKeys[KeyAlgorithm.Ed25519.name]
                                ?: throw IllegalArgumentException("sender claimed key should not be empty")
                        )
                        store.olm.updateInboundMegolmSession(sessionId, roomId) {
                            if (it != null && it.firstKnownIndex <= firstKnownIndex) it
                            else StoredInboundMegolmSession(
                                senderKey = data.senderKey,
                                sessionId = sessionId,
                                roomId = roomId,
                                firstKnownIndex = firstKnownIndex,
                                isTrusted = false, // because it comes from backup
                                hasBeenBackedUp = true, // because it comes from backup
                                senderSigningKey = senderSigningKey,
                                forwardingCurve25519KeyChain = data.forwardingKeyChain,
                                pickled = pickledSession
                            )
                        }
                    }
                }
                log.debug { "found key backup for roomId=$roomId, sessionId=$sessionId" }
                currentlyLoadingMegolmSessions.update { it - Pair(roomId, sessionId) }
            }
        }
    }

    override suspend fun keyBackupCanBeTrusted(
        keyBackupVersion: GetRoomKeysBackupVersionResponse,
        privateKey: String,
    ): Boolean {
        val generatedPublicKey = try {
            freeAfter(OlmPkDecryption.create(privateKey)) { it.publicKey }
        } catch (error: Throwable) {
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
//    if ( // TODO this is only relevant, when we want to use the key backup without private key
//        keyBackupVersion.authData.signatures[ownUserId]?.none {
//            it.keyId?.let { keyId ->
//                val keyTrustLevel = store.keys.getDeviceKey(ownUserId, keyId)?.trustLevel
//                    ?: store.keys.getCrossSigningKey(ownUserId, keyId)?.trustLevel
//                keyTrustLevel == KeySignatureTrustLevel.Valid(true)
//                        || keyTrustLevel == KeySignatureTrustLevel.CrossSigned(true)
//                        || keyTrustLevel == KeySignatureTrustLevel.NotAllDeviceKeysCrossSigned(true)
//            } == true
//        } == true
//    ) {
//        log.warn { "key backup cannot be trusted, because it is not signed by any trusted key" }
//        return false
//    }
        return true
    }

    @OptIn(FlowPreview::class)
    internal suspend fun uploadRoomKeyBackup() {
        currentSyncState.retryInfiniteWhenSyncIs(
            SyncState.RUNNING,
            onError = { log.warn(it) { "failed upload room key backup" } },
            onCancel = { log.debug { "stop upload room key backup, because job was cancelled" } },
        ) {
            store.olm.notBackedUpInboundMegolmSessions.debounce(1.seconds).onEach { notBackedUpInboundMegolmSessions ->
                val version = version.value
                if (version != null && notBackedUpInboundMegolmSessions.isNotEmpty()) {
                    log.debug { "upload room keys to key backup" }
                    api.keys.setRoomKeys(version.version, RoomsKeyBackup(
                        notBackedUpInboundMegolmSessions.values.groupBy { it.roomId }
                            .mapValues { roomEntries ->
                                RoomKeyBackup(roomEntries.value.associate { session ->
                                    val encryptedRoomKeyBackupV1SessionData =
                                        freeAfter(OlmPkEncryption.create(version.authData.publicKey.value)) { pke ->
                                            val sessionKey = freeAfter(
                                                OlmInboundGroupSession.unpickle(olmPickleKey, session.pickled)
                                            ) { it.export(it.firstKnownIndex) }
                                            pke.encrypt(
                                                api.json.encodeToString(
                                                    RoomKeyBackupV1SessionData(
                                                        session.senderKey,
                                                        session.forwardingCurve25519KeyChain,
                                                        session.senderSigningKey.let { mapOf(it.algorithm.name to it.value) },
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
                                updateKeyBackupVersion(store.keys.secrets.value[M_MEGOLM_BACKUP_V1]?.decryptedPrivateKey)
                            }
                        }
                    }.getOrThrow()
                    notBackedUpInboundMegolmSessions.values.forEach {
                        store.olm.updateInboundMegolmSession(it.sessionId, it.roomId) { session ->
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
        return api.keys.setRoomKeysVersion(
            SetRoomKeyBackupVersionRequest.V1(
                authData = with(
                    RoomKeyBackupAuthData.RoomKeyBackupV1AuthData(Key.Curve25519Key(null, keyBackupPublicKey))
                ) {
                    val ownDeviceSignature = olmSign.signatures(this)[ownUserId]
                        ?.firstOrNull()
                    val ownUsersSignature =
                        olmSign.signatures(this, Custom(masterSigningPrivateKey, masterSigningPublicKey))[ownUserId]
                            ?.firstOrNull()
                    requireNotNull(ownUsersSignature)
                    requireNotNull(ownDeviceSignature)
                    copy(signatures = signatures + (ownUserId to keysOf(ownDeviceSignature, ownUsersSignature)))
                },
                version = null // create new version
            )
        ).flatMap {
            val encryptedBackupKey = MegolmBackupV1EventContent(
                encryptSecret(key, keyId, M_MEGOLM_BACKUP_V1.id, keyBackupPrivateKey, api.json)
            )
            store.keys.secrets.update {
                it + (M_MEGOLM_BACKUP_V1 to StoredSecret(
                    ClientEvent.GlobalAccountDataEvent(encryptedBackupKey),
                    keyBackupPrivateKey
                ))
            }
            api.users.setAccountData(encryptedBackupKey, ownUserId)
        }
    }
}