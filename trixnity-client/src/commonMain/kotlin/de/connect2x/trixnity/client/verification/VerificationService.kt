package de.connect2x.trixnity.client.verification

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import de.connect2x.trixnity.client.CurrentSyncState
import de.connect2x.trixnity.client.key.KeySecretService
import de.connect2x.trixnity.client.key.KeyService
import de.connect2x.trixnity.client.key.KeyTrustService
import de.connect2x.trixnity.client.room.RoomService
import de.connect2x.trixnity.client.store.*
import de.connect2x.trixnity.client.user.UserService
import de.connect2x.trixnity.client.verification.ActiveVerificationState.Cancel
import de.connect2x.trixnity.client.verification.ActiveVerificationState.Done
import de.connect2x.trixnity.client.verification.VerificationService.SelfVerificationMethods
import de.connect2x.trixnity.client.verification.VerificationService.SelfVerificationMethods.PreconditionsNotMet
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.clientserverapi.client.SyncState
import de.connect2x.trixnity.clientserverapi.model.room.CreateRoom
import de.connect2x.trixnity.core.*
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import de.connect2x.trixnity.core.model.events.InitialStateEvent
import de.connect2x.trixnity.core.model.events.m.DirectEventContent
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationMethod
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationRequestToDeviceEventContent
import de.connect2x.trixnity.core.model.events.m.room.EncryptionEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent.VerificationRequest
import de.connect2x.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import de.connect2x.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import de.connect2x.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key
import de.connect2x.trixnity.crypto.core.SecureRandom
import de.connect2x.trixnity.crypto.driver.CryptoDriver
import de.connect2x.trixnity.crypto.olm.DecryptedOlmEventContainer
import de.connect2x.trixnity.crypto.olm.OlmDecrypter
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService
import de.connect2x.trixnity.utils.nextString
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger("de.connect2x.trixnity.client.verification.VerificationService")

interface VerificationService {
    val activeDeviceVerification: StateFlow<ActiveDeviceVerification?>

    val activeUserVerifications: StateFlow<List<ActiveUserVerification>>

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
         * Bootstrapping can be done with [KeyService::bootstrapCrossSigning][de.connect2x.trixnity.client.key.KeyServiceImpl.bootstrapCrossSigning].
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
    private val userService: UserService,
    private val keyService: KeyService,
    private val keyTrustService: KeyTrustService,
    private val keySecretService: KeySecretService,
    private val currentSyncState: CurrentSyncState,
    private val clock: Clock,
    private val driver: CryptoDriver,
) : VerificationService, EventHandler {
    private val ownUserId = userInfo.userId
    private val ownDeviceId = userInfo.deviceId
    private val _activeDeviceVerification = MutableStateFlow<ActiveDeviceVerificationImpl?>(null)
    override val activeDeviceVerification = _activeDeviceVerification.asStateFlow()
    private val _activeUserVerifications = MutableStateFlow<List<ActiveUserVerificationImpl>>(listOf())
    override val activeUserVerifications: StateFlow<List<ActiveUserVerificationImpl>> =
        _activeUserVerifications.asStateFlow()
    private val supportedMethods: Set<VerificationMethod> = setOf(Sas)

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeContent(subscriber = ::handleDeviceVerificationRequestEvents)
            .unsubscribeOnCompletion(scope)
        olmDecrypter.subscribe(::handleOlmDecryptedDeviceVerificationRequestEvents)
            .unsubscribeOnCompletion(scope)
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        scope.launch(start = UNDISPATCHED) {
            _activeUserVerifications.collect { startLifecycleOfActiveVerifications(it, this) }
        }
        scope.launch(start = UNDISPATCHED) {
            activeDeviceVerification.collect { it?.let { startLifecycleOfActiveVerifications(listOf(it), this) } }
        }
    }

    private suspend fun handleDeviceVerificationRequestEvents(event: ClientEvent<VerificationRequestToDeviceEventContent>) {
        val content = event.content
        when (event) {
            is ToDeviceEvent -> {
                if (isVerificationRequestActive(content.timestamp, clock)) {
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
                            clock = clock,
                            driver = driver,
                        ).cancel()
                    } else {
                        _activeDeviceVerification.getAndUpdate {
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
                                clock = clock,
                                driver = driver,
                            )
                        }?.cancel()
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
                if (isVerificationRequestActive(content.timestamp, clock)) {
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
                            clock = clock,
                            driver = driver,
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
                                clock = clock,
                                driver = driver,
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
                            _activeUserVerifications.update { it - verification }
                        }

                        is ActiveDeviceVerificationImpl -> {
                            _activeDeviceVerification.update { null }
                        }
                    }
                }
        }
    }

    // Equality for javascript
    private val createDeviceVerificationRequestStable = ::createDeviceVerificationRequest

    override suspend fun createDeviceVerificationRequest(
        theirUserId: UserId,
        theirDeviceIds: Set<String>
    ): Result<ActiveDeviceVerification> = kotlin.runCatching {
        log.info { "create new device verification request to $theirUserId ($theirDeviceIds)" }
        val request = VerificationRequestToDeviceEventContent(
            ownDeviceId, supportedMethods, clock.now().toEpochMilliseconds(), SecureRandom.nextString(22)
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
            clock = clock,
            driver = driver,
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
                    ?.firstOrNull {
                        userService.getById(it, theirUserId).firstOrNull()
                            ?.let { it.membership == Membership.JOIN } ?: false
                    }
                    ?: api.room.createRoom(
                        invite = setOf(theirUserId),
                        isDirect = true,
                        preset = CreateRoom.Request.Preset.TRUSTED_PRIVATE,
                        initialState = listOf(InitialStateEvent(EncryptionEventContent(), stateKey = "")),
                    ).getOrThrow()
            log.info { "put user verification into room $roomId" }
            val transactionId = roomService.sendMessage(roomId) {
                content(request)
            }
            val eventId = roomService.getOutbox(roomId, transactionId)
                .mapNotNull { it?.eventId }
                .first()

            ActiveUserVerificationImpl(
                request = request,
                requestIsFromOurOwn = true,
                requestEventId = eventId,
                requestTimestamp = clock.now().toEpochMilliseconds(),
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
                clock = clock,
                driver = driver,
            ).also { auv -> _activeUserVerifications.update { it + auv } }
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
                    } ?: run {
                        log.debug { "no default secret key found" }
                        emit(null)
                    }
                },
        ) { bootstrapRunning, currentSyncState, crossSigningKeys, deviceKeys, defaultKey ->
            log.trace {
                """
                    self verification preconditions:
                         bootstrapRunning=$bootstrapRunning
                         currentSyncState=$currentSyncState
                         crossSigningKeys=$crossSigningKeys
                         deviceKeys=$deviceKeys
                         defaultKey=$defaultKey
                """.trimIndent()
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

            val sendToDevices = deviceKeys
                .filterValues { it.trustLevel is KeySignatureTrustLevel.CrossSigned && @OptIn(MSC3814::class) it.value.signed.dehydrated != true }
                .keys - ownDeviceId

            val deviceVerificationMethod =
                if (sendToDevices.isNotEmpty())
                    setOf(
                        SelfVerificationMethod.CrossSignedDeviceVerification(
                            ownUserId,
                            sendToDevices.toSet(),
                            createDeviceVerificationRequestStable
                        )
                    )
                else setOf()

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
        return if (isVerificationRequestActive(timelineEvent.event.originTimestamp, clock)) {
            getActiveUserVerificationMutex.withLock {
                val cache =
                    _activeUserVerifications.value.find { it.roomId == roomId && it.relatesTo?.eventId == eventId }
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
                            clock = clock,
                            driver = driver,
                        ).also { auv -> _activeUserVerifications.update { it + auv } }
                    } else null
                }
            }
        } else null
    }
}