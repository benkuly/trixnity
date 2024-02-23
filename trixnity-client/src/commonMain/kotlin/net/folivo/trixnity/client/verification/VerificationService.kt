package net.folivo.trixnity.client.verification

import com.benasher44.uuid.uuid4
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.CurrentSyncState
import net.folivo.trixnity.client.key.KeySecretService
import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.client.key.KeyTrustService
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.verification.ActiveVerificationState.Cancel
import net.folivo.trixnity.client.verification.ActiveVerificationState.Done
import net.folivo.trixnity.client.verification.VerificationService.SelfVerificationMethods
import net.folivo.trixnity.client.verification.VerificationService.SelfVerificationMethods.PreconditionsNotMet
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationRequestToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.VerificationRequest
import net.folivo.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key
import net.folivo.trixnity.core.subscribeContent
import net.folivo.trixnity.core.unsubscribeOnCompletion
import net.folivo.trixnity.crypto.olm.DecryptedOlmEventContainer
import net.folivo.trixnity.crypto.olm.OlmDecrypter
import net.folivo.trixnity.crypto.olm.OlmEncryptionService
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

interface VerificationService {
    val activeDeviceVerification: StateFlow<ActiveDeviceVerification?>

    suspend fun createDeviceVerificationRequest(
        theirUserId: UserId,
        theirDeviceIds: Set<String>
    ): Result<ActiveDeviceVerification>

    suspend fun createUserVerificationRequest(
        theirUserId: UserId
    ): Result<ActiveUserVerification>

    /**
     * Possible states include:
     * * [SelfVerificationMethods.PreconditionsNotMet]
     * * [SelfVerificationMethods.NoCrossSigningEnabled]
     * * [SelfVerificationMethods.AlreadyCrossSigned]
     * * [SelfVerificationMethods.CrossSigningEnabled]
     */
    sealed interface SelfVerificationMethods {
        /**
         * We don't have enough information yet to calculated available methods (e.g. waiting for the first sync).
         */
        data class PreconditionsNotMet(val reasons: Set<Reason>) : SelfVerificationMethods {
            interface Reason {
                data object SyncNotRunning : Reason
                data object DeviceKeysNotFetchedYet : Reason
                data object CrossSigningKeysNotFetchedYet : Reason
            }
        }

        /**
         * Cross signing can be bootstrapped.
         * Bootstrapping can be done with [KeyService::bootstrapCrossSigning][net.folivo.trixnity.client.key.KeyServiceImpl.bootstrapCrossSigning].
         */
        data object NoCrossSigningEnabled : SelfVerificationMethods

        /**
         * No self verification needed.
         */
        data object AlreadyCrossSigned : SelfVerificationMethods

        /**
         * If empty: no other device & no key backup -> consider new bootstrapping of cross signing
         */
        data class CrossSigningEnabled(val methods: Set<SelfVerificationMethod>) : SelfVerificationMethods
    }

    fun getSelfVerificationMethods(): Flow<SelfVerificationMethods>

    @Deprecated(
        "use eventId instead",
        ReplaceWith("getActiveUserVerification(timelineEvent.roomId, timelineEvent.eventId)")
    )
    suspend fun getActiveUserVerification(
        timelineEvent: TimelineEvent,
    ): ActiveUserVerification?

    suspend fun getActiveUserVerification(
        roomId: RoomId,
        eventId: EventId,
    ): ActiveUserVerification?
}

class VerificationServiceImpl(
    userInfo: UserInfo,
    private val api: MatrixClientServerApiClient,
    private val keyStore: KeyStore,
    private val globalAccountDataStore: GlobalAccountDataStore,
    private val olmDecrypter: OlmDecrypter,
    private val olmEncryptionService: OlmEncryptionService,
    private val roomService: RoomService,
    private val keyService: KeyService,
    private val keyTrustService: KeyTrustService,
    private val keySecretService: KeySecretService,
    private val currentSyncState: CurrentSyncState,
) : VerificationService, EventHandler {
    private val ownUserId = userInfo.userId
    private val ownDeviceId = userInfo.deviceId
    private val _activeDeviceVerification = MutableStateFlow<ActiveDeviceVerificationImpl?>(null)
    override val activeDeviceVerification = _activeDeviceVerification.asStateFlow()
    private val activeUserVerifications = MutableStateFlow<List<ActiveUserVerificationImpl>>(listOf())
    private val supportedMethods: Set<VerificationMethod> = setOf(Sas)

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeContent(subscriber = ::handleDeviceVerificationRequestEvents)
            .unsubscribeOnCompletion(scope)
        olmDecrypter.subscribe(::handleOlmDecryptedDeviceVerificationRequestEvents)
            .unsubscribeOnCompletion(scope)
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        scope.launch(start = UNDISPATCHED) {
            activeUserVerifications.collect { startLifecycleOfActiveVerifications(it, this) }
        }
        scope.launch(start = UNDISPATCHED) {
            activeDeviceVerification.collect { it?.let { startLifecycleOfActiveVerifications(listOf(it), this) } }
        }
    }

    private suspend fun handleDeviceVerificationRequestEvents(event: ClientEvent<VerificationRequestToDeviceEventContent>) {
        val content = event.content
        when (event) {
            is ToDeviceEvent -> {
                if (isVerificationRequestActive(content.timestamp)) {
                    log.info { "got new device verification request from ${event.sender}" }
                    if (_activeDeviceVerification.value != null) {
                        log.info { "already have an active device verification -> cancelling new verification request" }
                        ActiveDeviceVerificationImpl(
                            request = event.content,
                            requestIsOurs = false,
                            ownUserId = ownUserId,
                            ownDeviceId = ownDeviceId,
                            theirUserId = event.sender,
                            theirDeviceId = content.fromDevice,
                            supportedMethods = supportedMethods,
                            api = api,
                            olmDecrypter = olmDecrypter,
                            olmEncryptionService = olmEncryptionService,
                            keyStore = keyStore,
                            keyTrust = keyTrustService,
                        ).cancel()
                    } else {
                        _activeDeviceVerification.value =
                            ActiveDeviceVerificationImpl(
                                request = event.content,
                                requestIsOurs = false,
                                ownUserId = ownUserId,
                                ownDeviceId = ownDeviceId,
                                theirUserId = event.sender,
                                theirDeviceId = content.fromDevice,
                                supportedMethods = supportedMethods,
                                api = api,
                                olmDecrypter = olmDecrypter,
                                olmEncryptionService = olmEncryptionService,
                                keyTrust = keyTrustService,
                                keyStore = keyStore,
                            )
                    }
                } else {
                    log.warn { "Received device verification request that is not active anymore: $event" }
                }
            }

            else -> log.warn { "got new device verification request with an event type ${event::class.simpleName}, that we did not expected" }
        }
    }

    private suspend fun handleOlmDecryptedDeviceVerificationRequestEvents(event: DecryptedOlmEventContainer) {
        when (val content = event.decrypted.content) {
            is VerificationRequestToDeviceEventContent -> {
                if (isVerificationRequestActive(content.timestamp)) {
                    log.info { "got new device verification request from ${event.decrypted.sender}" }
                    if (_activeDeviceVerification.value != null) {
                        log.info { "already have an active device verification -> cancelling new verification request" }
                        ActiveDeviceVerificationImpl(
                            request = content,
                            requestIsOurs = false,
                            ownUserId = ownUserId,
                            ownDeviceId = ownDeviceId,
                            theirUserId = event.decrypted.sender,
                            theirDeviceId = content.fromDevice,
                            supportedMethods = supportedMethods,
                            api = api,
                            olmDecrypter = olmDecrypter,
                            olmEncryptionService = olmEncryptionService,
                            keyTrust = keyTrustService,
                            keyStore = keyStore,
                        ).cancel("already have an active device verification")
                    } else {
                        _activeDeviceVerification.value =
                            ActiveDeviceVerificationImpl(
                                request = content,
                                requestIsOurs = false,
                                ownUserId = ownUserId,
                                ownDeviceId = ownDeviceId,
                                theirUserId = event.decrypted.sender,
                                theirDeviceId = content.fromDevice,
                                supportedMethods = supportedMethods,
                                api = api,
                                olmDecrypter = olmDecrypter,
                                olmEncryptionService = olmEncryptionService,
                                keyTrust = keyTrustService,
                                keyStore = keyStore,
                            )
                    }
                }
            }

            else -> {}
        }
    }

    private suspend fun startLifecycleOfActiveVerifications(
        verifications: List<ActiveVerificationImpl>,
        scope: CoroutineScope
    ) {
        verifications.forEach { verification ->
            val started = verification.startLifecycle(scope)
            if (started)
                scope.launch {
                    verification.state.first { verification.state.value is Done || verification.state.value is Cancel }
                    when (verification) {
                        is ActiveUserVerificationImpl -> {
                            delay(20.minutes)
                            activeUserVerifications.update { it - verification }
                        }

                        is ActiveDeviceVerificationImpl -> {
                            _activeDeviceVerification.update { null }
                        }
                    }
                }
        }
    }

    override suspend fun createDeviceVerificationRequest(
        theirUserId: UserId,
        theirDeviceIds: Set<String>
    ): Result<ActiveDeviceVerification> = kotlin.runCatching {
        log.info { "create new device verification request to $theirUserId ($theirDeviceIds)" }
        val request = VerificationRequestToDeviceEventContent(
            ownDeviceId, supportedMethods, Clock.System.now().toEpochMilliseconds(), uuid4().toString()
        )
        api.user.sendToDevice(mapOf(theirUserId to theirDeviceIds.toSet().associateWith {
            olmEncryptionService.encryptOlm(request, theirUserId, it).getOrNull() ?: request
        })).getOrThrow()
        ActiveDeviceVerificationImpl(
            request = request,
            requestIsOurs = true,
            ownUserId = ownUserId,
            ownDeviceId = ownDeviceId,
            theirUserId = theirUserId,
            theirDeviceIds = theirDeviceIds.toSet(),
            supportedMethods = supportedMethods,
            api = api,
            olmDecrypter = olmDecrypter,
            olmEncryptionService = olmEncryptionService,
            keyTrust = keyTrustService,
            keyStore = keyStore,
        ).also { newDeviceVerification ->
            _activeDeviceVerification.getAndUpdate { newDeviceVerification }?.cancel()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun createUserVerificationRequest(
        theirUserId: UserId
    ): Result<ActiveUserVerification> = kotlin.runCatching {
        coroutineScope {
            log.info { "create new user verification request to $theirUserId" }
            val request = VerificationRequest(ownDeviceId, theirUserId, supportedMethods)
            val roomId =
                globalAccountDataStore.get<DirectEventContent>().first()?.content?.mappings?.get(theirUserId)
                    ?.firstOrNull()
                    ?: api.room.createRoom(invite = setOf(theirUserId), isDirect = true).getOrThrow()
            val transactionId = roomService.sendMessage(roomId) {
                content(request)
            }
            val eventId = roomService.getOutbox()
                .flatMapLatest { it[transactionId] ?: flowOf(null) }
                .mapNotNull { it?.eventId }
                .first()
            ActiveUserVerificationImpl(
                request = request,
                requestIsFromOurOwn = true,
                requestEventId = eventId,
                requestTimestamp = Clock.System.now().toEpochMilliseconds(),
                ownUserId = ownUserId,
                ownDeviceId = ownDeviceId,
                theirUserId = theirUserId,
                theirInitialDeviceId = null,
                roomId = roomId,
                supportedMethods = supportedMethods,
                json = api.json,
                keyStore = keyStore,
                room = roomService,
                keyTrust = keyTrustService,
            ).also { auv -> activeUserVerifications.update { it + auv } }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getSelfVerificationMethods(): Flow<SelfVerificationMethods> {
        return combine(
            keyService.bootstrapRunning,
            currentSyncState,
            keyStore.getCrossSigningKeys(ownUserId),
            keyStore.getDeviceKeys(ownUserId),
            globalAccountDataStore.get<DefaultSecretKeyEventContent>()
                .transformLatest { event ->
                    event?.content?.key?.let {
                        emitAll(globalAccountDataStore.get<SecretKeyEventContent>(it))
                    } ?: emit(null)
                },
        ) { bootstrapRunning, currentSyncState, crossSigningKeys, deviceKeys, defaultKey ->
            log.trace {
                "self verification preconditions: bootstrapRunning=$bootstrapRunning currentSyncState=$currentSyncState " +
                        "crossSigningKeys=$crossSigningKeys deviceKeys=$deviceKeys defaultKey=$defaultKey"
            }
            // preconditions: sync running, login was successful and we are not yet cross-signed
            if (currentSyncState != SyncState.RUNNING || deviceKeys == null || crossSigningKeys == null)
                return@combine PreconditionsNotMet(
                    setOfNotNull(
                        if (currentSyncState != SyncState.RUNNING) PreconditionsNotMet.Reason.SyncNotRunning else null,
                        if (deviceKeys == null) PreconditionsNotMet.Reason.DeviceKeysNotFetchedYet else null,
                        if (crossSigningKeys == null) PreconditionsNotMet.Reason.CrossSigningKeysNotFetchedYet else null
                    )
                )
            val ownTrustLevel = deviceKeys[ownDeviceId]?.trustLevel
            if (ownTrustLevel == KeySignatureTrustLevel.CrossSigned(true)) return@combine SelfVerificationMethods.AlreadyCrossSigned

            // we need bootstrapping if this is the first device or bootstrapping is in progress
            if (crossSigningKeys.isEmpty()) return@combine SelfVerificationMethods.NoCrossSigningEnabled
            if (bootstrapRunning) return@combine SelfVerificationMethods.NoCrossSigningEnabled

            val deviceVerificationMethod = deviceKeys.entries
                .filter { it.value.trustLevel is KeySignatureTrustLevel.CrossSigned }
                .map { it.key }
                .let {
                    val sendToDevices = it - ownDeviceId
                    if (sendToDevices.isNotEmpty())
                        setOf(
                            SelfVerificationMethod.CrossSignedDeviceVerification(
                                ownUserId,
                                sendToDevices.toSet(),
                                ::createDeviceVerificationRequest
                            )
                        )
                    else setOf()
                }

            val recoveryKeyMethods = when (val content = defaultKey?.content) {
                is AesHmacSha2Key -> when (content.passphrase) {
                    is AesHmacSha2Key.SecretStorageKeyPassphrase.Pbkdf2 ->
                        setOf(
                            SelfVerificationMethod.AesHmacSha2RecoveryKeyWithPbkdf2Passphrase(
                                keySecretService,
                                keyTrustService,
                                defaultKey.key,
                                content
                            ),
                            SelfVerificationMethod.AesHmacSha2RecoveryKey(
                                keySecretService,
                                keyTrustService,
                                defaultKey.key,
                                content
                            )
                        )

                    is AesHmacSha2Key.SecretStorageKeyPassphrase.Unknown, null ->
                        setOf(
                            SelfVerificationMethod.AesHmacSha2RecoveryKey(
                                keySecretService,
                                keyTrustService,
                                defaultKey.key,
                                content
                            )
                        )
                }

                is SecretKeyEventContent.Unknown, null -> setOf()
            }

            return@combine SelfVerificationMethods.CrossSigningEnabled(recoveryKeyMethods + deviceVerificationMethod)
        }
    }

    private val getActiveUserVerificationMutex = Mutex()

    @Deprecated(
        "use eventId instead",
        replaceWith = ReplaceWith("getActiveUserVerification(timelineEvent.roomId, timelineEvent.eventId)")
    )
    override suspend fun getActiveUserVerification(
        timelineEvent: TimelineEvent,
    ): ActiveUserVerification? = getActiveUserVerification(timelineEvent.roomId, timelineEvent.eventId)

    override suspend fun getActiveUserVerification(
        roomId: RoomId,
        eventId: EventId,
    ): ActiveUserVerification? {
        val timelineEvent =
            withTimeoutOrNull(6.seconds) {
                roomService.getTimelineEvent(roomId, eventId) { decryptionTimeout = 5.seconds }
                    .filter { it?.content != null }.first()
            } ?: return null
        val request = timelineEvent.content?.getOrNull() as? VerificationRequest ?: return null
        return if (isVerificationRequestActive(timelineEvent.event.originTimestamp)) {
            getActiveUserVerificationMutex.withLock {
                val cache =
                    activeUserVerifications.value.find { it.roomId == roomId && it.relatesTo?.eventId == eventId }
                if (cache != null) cache
                else {
                    val sender = timelineEvent.event.sender
                    if (sender != ownUserId && request.to == ownUserId) {
                        ActiveUserVerificationImpl(
                            request = request,
                            requestIsFromOurOwn = false,
                            requestEventId = eventId,
                            requestTimestamp = timelineEvent.event.originTimestamp,
                            ownUserId = ownUserId,
                            ownDeviceId = ownDeviceId,
                            theirUserId = sender,
                            theirInitialDeviceId = request.fromDevice,
                            roomId = roomId,
                            supportedMethods = supportedMethods,
                            json = api.json,
                            keyStore = keyStore,
                            room = roomService,
                            keyTrust = keyTrustService,
                        ).also { auv -> activeUserVerifications.update { it + auv } }
                    } else null
                }
            }
        } else null
    }
}