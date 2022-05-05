package net.folivo.trixnity.client.verification

import com.benasher44.uuid.uuid4
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import mu.KotlinLogging
import net.folivo.trixnity.client.crypto.IOlmEventService
import net.folivo.trixnity.client.crypto.IOlmService
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel
import net.folivo.trixnity.client.key.IKeyService
import net.folivo.trixnity.client.possiblyEncryptEvent
import net.folivo.trixnity.client.room.IRoomService
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.get
import net.folivo.trixnity.client.user.IUserService
import net.folivo.trixnity.client.verification.ActiveVerificationState.Cancel
import net.folivo.trixnity.client.verification.ActiveVerificationState.Done
import net.folivo.trixnity.client.verification.IVerificationService.SelfVerificationMethods
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationRequestEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.VerificationRequestMessageEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key
import net.folivo.trixnity.core.subscribe
import kotlin.jvm.JvmInline
import kotlin.time.Duration.Companion.minutes

private val log = KotlinLogging.logger {}

interface IVerificationService {
    val activeDeviceVerification: StateFlow<ActiveDeviceVerification?>

    suspend fun createDeviceVerificationRequest(
        theirUserId: UserId,
        vararg theirDeviceIds: String
    ): Result<ActiveDeviceVerification>

    suspend fun createUserVerificationRequest(
        theirUserId: UserId
    ): Result<ActiveUserVerification>

    interface SelfVerificationMethods {
        /**
         * We don't have enough information yet to calculated available methods (e.g. waiting for the first sync).
         */
        object PreconditionsNotMet : SelfVerificationMethods

        /**
         * Ccross signing needs to be bootstrapped.
         * Bootstrapping can be done with [KeyService::bootstrapCrossSigning][net.folivo.trixnity.client.key.KeyService.bootstrapCrossSigning].
         */
        object NoCrossSigningEnabled : SelfVerificationMethods

        object AlreadyCrossSigned : SelfVerificationMethods

        /**
         * If empty: no other device & no key backup -> consider start bootstrapping.
         */
        @JvmInline
        value class CrossSigningEnabled(val methods: Set<SelfVerificationMethod>) : SelfVerificationMethods
    }

    suspend fun getSelfVerificationMethods(scope: CoroutineScope): StateFlow<SelfVerificationMethods>

    suspend fun getActiveUserVerification(
        timelineEvent: TimelineEvent
    ): ActiveUserVerification?
}

class VerificationService(
    private val ownUserId: UserId,
    private val ownDeviceId: String,
    private val api: MatrixClientServerApiClient,
    private val store: Store,
    private val olmEventService: IOlmEventService,
    private val roomService: IRoomService,
    private val userService: IUserService,
    private val keyService: IKeyService,
    private val supportedMethods: Set<VerificationMethod> = setOf(Sas),
    private val currentSyncState: StateFlow<SyncState>,
) : IVerificationService {
    private val _activeDeviceVerification = MutableStateFlow<ActiveDeviceVerification?>(null)
    override val activeDeviceVerification = _activeDeviceVerification.asStateFlow()
    private val activeUserVerifications = MutableStateFlow<List<ActiveUserVerification>>(listOf())

    internal suspend fun start(scope: CoroutineScope) {
        api.sync.subscribe(::handleDeviceVerificationRequestEvents)
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        scope.launch(start = UNDISPATCHED) {
            olmEventService.decryptedOlmEvents.collect(::handleOlmDecryptedDeviceVerificationRequestEvents)
        }
        scope.launch(start = UNDISPATCHED) {
            activeUserVerifications.collect { startLifecycleOfActiveVerifications(it, this) }
        }
        scope.launch(start = UNDISPATCHED) {
            activeDeviceVerification.collect { it?.let { startLifecycleOfActiveVerifications(listOf(it), this) } }
        }
    }

    private suspend fun handleDeviceVerificationRequestEvents(event: Event<VerificationRequestEventContent>) {
        val content = event.content
        when (event) {
            is Event.ToDeviceEvent -> {
                if (isVerificationRequestActive(content.timestamp)) {
                    log.info { "got new device verification request from ${event.sender}" }
                    if (_activeDeviceVerification.value != null) {
                        log.info { "already have an active device verification -> cancelling new verification request" }
                        ActiveDeviceVerification(
                            request = event.content,
                            requestIsOurs = false,
                            ownUserId = ownUserId,
                            ownDeviceId = ownDeviceId,
                            theirUserId = event.sender,
                            theirDeviceId = content.fromDevice,
                            supportedMethods = supportedMethods,
                            api = api,
                            olmEvent = olmEventService,
                            store = store,
                            keyTrust = keyService.trust,
                        ).cancel()
                    } else {
                        _activeDeviceVerification.value =
                            ActiveDeviceVerification(
                                request = event.content,
                                requestIsOurs = false,
                                ownUserId = ownUserId,
                                ownDeviceId = ownDeviceId,
                                theirUserId = event.sender,
                                theirDeviceId = content.fromDevice,
                                supportedMethods = supportedMethods,
                                api = api,
                                olmEvent = olmEventService,
                                keyTrust = keyService.trust,
                                store = store,
                            )
                    }
                } else {
                    log.warn { "Received device verification request that is not active anymore: $event" }
                }
            }
            else -> log.warn { "got new device verification request with an event type ${event::class.simpleName}, that we did not expected" }
        }
    }

    private suspend fun handleOlmDecryptedDeviceVerificationRequestEvents(event: IOlmService.DecryptedOlmEventContainer) {
        when (val content = event.decrypted.content) {
            is VerificationRequestEventContent -> {
                if (isVerificationRequestActive(content.timestamp)) {
                    log.info { "got new device verification request from ${event.decrypted.sender}" }
                    if (_activeDeviceVerification.value != null) {
                        log.info { "already have an active device verification -> cancelling new verification request" }
                        ActiveDeviceVerification(
                            request = content,
                            requestIsOurs = false,
                            ownUserId = ownUserId,
                            ownDeviceId = ownDeviceId,
                            theirUserId = event.decrypted.sender,
                            theirDeviceId = content.fromDevice,
                            supportedMethods = supportedMethods,
                            api = api,
                            olmEvent = olmEventService,
                            keyTrust = keyService.trust,
                            store = store,
                        ).cancel()
                    } else {
                        _activeDeviceVerification.value =
                            ActiveDeviceVerification(
                                request = content,
                                requestIsOurs = false,
                                ownUserId = ownUserId,
                                ownDeviceId = ownDeviceId,
                                theirUserId = event.decrypted.sender,
                                theirDeviceId = content.fromDevice,
                                supportedMethods = supportedMethods,
                                api = api,
                                olmEvent = olmEventService,
                                keyTrust = keyService.trust,
                                store = store,
                            )
                    }
                }
            }
        }
    }

    private suspend fun startLifecycleOfActiveVerifications(
        verifications: List<ActiveVerification>,
        scope: CoroutineScope
    ) {
        verifications.forEach { verification ->
            val started = verification.startLifecycle(scope)
            if (started)
                scope.launch {
                    verification.state.first { verification.state.value is Done || verification.state.value is Cancel }
                    when (verification) {
                        is ActiveUserVerification -> {
                            delay(20.minutes)
                            activeUserVerifications.update { it - verification }
                        }
                        is ActiveDeviceVerification -> {
                            _activeDeviceVerification.update { null }
                        }
                    }
                }
        }
    }

    override suspend fun createDeviceVerificationRequest(
        theirUserId: UserId,
        vararg theirDeviceIds: String
    ): Result<ActiveDeviceVerification> = kotlin.runCatching {
        log.info { "create new device verification request to $theirUserId ($theirDeviceIds)" }
        val request = VerificationRequestEventContent(
            ownDeviceId, supportedMethods, Clock.System.now().toEpochMilliseconds(), uuid4().toString()
        )
        api.users.sendToDevice(mapOf(theirUserId to theirDeviceIds.toSet().associateWith {
            try {
                olmEventService.encryptOlm(request, theirUserId, it)
            } catch (error: Exception) {
                request
            }
        })).getOrThrow()
        ActiveDeviceVerification(
            request = request,
            requestIsOurs = true,
            ownUserId = ownUserId,
            ownDeviceId = ownDeviceId,
            theirUserId = theirUserId,
            theirDeviceIds = theirDeviceIds.toSet(),
            supportedMethods = supportedMethods,
            api = api,
            olmEvent = olmEventService,
            keyTrust = keyService.trust,
            store = store,
        ).also { newDeviceVerification ->
            _activeDeviceVerification.getAndUpdate { newDeviceVerification }?.cancel()
        }
    }

    override suspend fun createUserVerificationRequest(
        theirUserId: UserId
    ): Result<ActiveUserVerification> = kotlin.runCatching {
        log.info { "create new user verification request to $theirUserId" }
        val request = VerificationRequestMessageEventContent(ownDeviceId, theirUserId, supportedMethods)
        val roomId =
            store.globalAccountData.get<DirectEventContent>()?.content?.mappings?.get(theirUserId)
                ?.firstOrNull()
                ?: api.rooms.createRoom(invite = setOf(theirUserId), isDirect = true).getOrThrow()
        val sendContent = try {
            possiblyEncryptEvent(request, roomId, store, olmEventService, userService)
        } catch (error: Exception) {
            request
        }
        val eventId = api.rooms.sendMessageEvent(roomId, sendContent).getOrThrow()
        ActiveUserVerification(
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
            api = api,
            store = store,
            olmEvent = olmEventService,
            user = userService,
            room = roomService,
            keyTrust = keyService.trust,
        ).also { auv -> activeUserVerifications.update { it + auv } }
    }

    /**
     * This should be called on login. If it is null, it means, that we don't have enough information yet to calculate
     * available methods or this device is already verified.
     * If it is empty, it means, that cross signing needs to be bootstrapped.
     * Bootstrapping can be done with [KeyService::bootstrapCrossSigning][net.folivo.trixnity.client.key.KeyService.bootstrapCrossSigning].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getSelfVerificationMethods(scope: CoroutineScope): StateFlow<SelfVerificationMethods> {
        return combine(
            keyService.bootstrapRunning,
            currentSyncState,
            store.keys.getCrossSigningKeys(ownUserId, scope),
            store.keys.getDeviceKeys(ownUserId, scope),
            store.globalAccountData.get<DefaultSecretKeyEventContent>(scope = scope)
                .transformLatest { event ->
                    coroutineScope {
                        event?.content?.key?.let {
                            emitAll(store.globalAccountData.get<SecretKeyEventContent>(it, this))
                        } ?: emit(null)
                    }
                },
        ) { bootstrapRunning, currentSyncState, crossSigningKeys, deviceKeys, defaultKey ->
            log.trace {
                "self verification preconditions: bootstrapRunning=$bootstrapRunning currentSyncState=$currentSyncState " +
                        "crossSigningKeys=$crossSigningKeys deviceKeys=$deviceKeys defaultKey=$defaultKey"
            }
            // preconditions: sync running, login was successful and we are not yet cross-signed
            if (deviceKeys == null || crossSigningKeys == null) return@combine SelfVerificationMethods.PreconditionsNotMet
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
                                sendToDevices,
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
                                keyService,
                                defaultKey.key,
                                content
                            ),
                            SelfVerificationMethod.AesHmacSha2RecoveryKey(keyService, defaultKey.key, content)
                        )
                    is AesHmacSha2Key.SecretStorageKeyPassphrase.Unknown, null ->
                        setOf(SelfVerificationMethod.AesHmacSha2RecoveryKey(keyService, defaultKey.key, content))
                }
                is SecretKeyEventContent.Unknown, null -> setOf()
            }

            return@combine SelfVerificationMethods.CrossSigningEnabled(recoveryKeyMethods + deviceVerificationMethod)
        }.stateIn(scope)
    }

    private val getActiveUserVerificationMutex = Mutex()
    override suspend fun getActiveUserVerification(
        timelineEvent: TimelineEvent
    ): ActiveUserVerification? {
        return if (isVerificationRequestActive(timelineEvent.event.originTimestamp)) {
            getActiveUserVerificationMutex.withLock {
                val cache =
                    activeUserVerifications.value.find { it.roomId == timelineEvent.roomId && it.relatesTo?.eventId == timelineEvent.eventId }
                if (cache != null) cache
                else {
                    val eventContent = timelineEvent.content?.getOrNull()
                    val request =
                        if (eventContent is VerificationRequestMessageEventContent) eventContent
                        else null
                    val sender = timelineEvent.event.sender
                    if (request != null && sender != ownUserId && request.to == ownUserId) {
                        ActiveUserVerification(
                            request = request,
                            requestIsFromOurOwn = false,
                            requestEventId = timelineEvent.eventId,
                            requestTimestamp = timelineEvent.event.originTimestamp,
                            ownUserId = ownUserId,
                            ownDeviceId = ownDeviceId,
                            theirUserId = sender,
                            theirInitialDeviceId = request.fromDevice,
                            roomId = timelineEvent.roomId,
                            supportedMethods = supportedMethods,
                            api = api,
                            store = store,
                            olmEvent = olmEventService,
                            user = userService,
                            room = roomService,
                            keyTrust = keyService.trust,
                        ).also { auv -> activeUserVerifications.update { it + auv } }
                    } else null
                }
            }
        } else null
    }
}