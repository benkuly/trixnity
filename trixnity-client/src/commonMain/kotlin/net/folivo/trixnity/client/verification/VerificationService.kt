package net.folivo.trixnity.client.verification

import com.benasher44.uuid.uuid4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import mu.KotlinLogging
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.client.possiblyEncryptEvent
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.get
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.client.verification.ActiveVerificationState.Cancel
import net.folivo.trixnity.client.verification.ActiveVerificationState.Done
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationRequestEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.VerificationRequestMessageEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.olm.OlmLibraryException

private val log = KotlinLogging.logger {}

class VerificationService(
    private val ownUserId: UserId,
    private val ownDeviceId: String,
    private val api: MatrixApiClient,
    private val store: Store,
    private val olmService: OlmService,
    private val roomService: RoomService,
    private val userService: UserService,
    private val keyService: KeyService,
    private val supportedMethods: Set<VerificationMethod> = setOf(Sas),
) {
    private val _activeDeviceVerification = MutableStateFlow<ActiveDeviceVerification?>(null)
    val activeDeviceVerification = _activeDeviceVerification.asStateFlow()
    private val activeUserVerifications = MutableStateFlow<List<ActiveUserVerification>>(listOf())

    internal suspend fun start(scope: CoroutineScope) {
        api.sync.subscribe(::handleDeviceVerificationRequestEvents)
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        scope.launch(start = UNDISPATCHED) {
            olmService.decryptedOlmEvents.collect(::handleOlmDecryptedDeviceVerificationRequestEvents)
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
                            olm = olmService,
                            store = store,
                            key = keyService,
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
                                olm = olmService,
                                key = keyService,
                                store = store,
                            )
                    }
                }
            }
            else -> log.warn { "got new device verification request with an event type ${event::class.simpleName}, that we did not expected" }
        }
    }

    private suspend fun handleOlmDecryptedDeviceVerificationRequestEvents(event: OlmService.DecryptedOlmEvent) {
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
                            olm = olmService,
                            key = keyService,
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
                                olm = olmService,
                                key = keyService,
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
                        is ActiveUserVerification -> activeUserVerifications.update { it - verification }
                        is ActiveDeviceVerification -> {
                            _activeDeviceVerification.update { null }
                        }
                    }
                }
        }
    }

    suspend fun createDeviceVerificationRequest(theirUserId: UserId, vararg theirDeviceIds: String) {
        log.info { "create new device verification request to $theirUserId ($theirDeviceIds)" }
        val request = VerificationRequestEventContent(
            ownDeviceId, supportedMethods, Clock.System.now().toEpochMilliseconds(), uuid4().toString()
        )
        api.users.sendToDevice(mapOf(theirUserId to theirDeviceIds.toSet().associateWith {
            try {
                olmService.events.encryptOlm(request, theirUserId, it)
            } catch (olmError: OlmLibraryException) {
                request
            }
        }))
        val existingDeviceVerification = _activeDeviceVerification.getAndUpdate {
            ActiveDeviceVerification(
                request = request,
                requestIsOurs = true,
                ownUserId = ownUserId,
                ownDeviceId = ownDeviceId,
                theirUserId = theirUserId,
                theirDeviceIds = theirDeviceIds.toSet(),
                supportedMethods = supportedMethods,
                api = api,
                olm = olmService,
                key = keyService,
                store = store,
            )
        }
        existingDeviceVerification?.cancel()
    }

    suspend fun createUserVerificationRequest(theirUserId: UserId): Result<Unit> = kotlin.runCatching {
        log.info { "create new user verification request to $theirUserId" }
        val request = VerificationRequestMessageEventContent(ownDeviceId, theirUserId, supportedMethods)
        val roomId =
            store.globalAccountData.get<DirectEventContent>()?.content?.mappings?.get(theirUserId)
                ?.firstOrNull()
                ?: api.rooms.createRoom(invite = setOf(theirUserId), isDirect = true).getOrThrow()
        val sendContent = try {
            possiblyEncryptEvent(request, roomId, store, olmService, userService)
        } catch (olmError: OlmLibraryException) {
            request
        }
        api.rooms.sendMessageEvent(roomId, sendContent).getOrThrow()
    }

    /**
     * This should be called on login. If it is null, it means, that we don't have enough information yet to calculated available methods.
     * If it is empty, it means that cross signing needs to be bootstrapped.
     * Bootstrapping can be done with [KeyService][net.folivo.trixnity.client.key.KeyService].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun getSelfVerificationMethods(scope: CoroutineScope): StateFlow<Set<SelfVerificationMethod>?> {
        return combine(
            store.keys.getDeviceKeys(ownUserId, scope),
            store.globalAccountData.get<DefaultSecretKeyEventContent>(scope = scope)
                .flatMapLatest { event ->
                    event?.content?.key?.let { store.globalAccountData.get<SecretKeyEventContent>(it, scope) }
                        ?: flowOf(null)
                },
        ) { deviceKeys, defaultKey ->
            if (deviceKeys == null) return@combine null

            if (deviceKeys[ownDeviceId]?.trustLevel != KeySignatureTrustLevel.NotCrossSigned)
                return@combine setOf()

            val deviceVerificationMethod = deviceKeys.entries
                .filter { it.value.trustLevel is KeySignatureTrustLevel.CrossSigned }
                .map { it.key }
                .let {
                    val sendToDevices = it - ownDeviceId
                    if (sendToDevices.isNotEmpty())
                        setOf(SelfVerificationMethod.CrossSignedDeviceVerification {
                            createDeviceVerificationRequest(ownUserId, *sendToDevices.toTypedArray())
                        })
                    else setOf()
                }

            val recoveryKeyMethods = when (val content = defaultKey?.content) {
                is SecretKeyEventContent.AesHmacSha2Key -> when (content.passphrase) {
                    is SecretKeyEventContent.SecretStorageKeyPassphrase.Pbkdf2 ->
                        setOf(
                            SelfVerificationMethod.AesHmacSha2RecoveryKeyWithPbkdf2Passphrase(
                                keyService,
                                defaultKey.key,
                                content
                            ),
                            SelfVerificationMethod.AesHmacSha2RecoveryKey(keyService, defaultKey.key, content)
                        )
                    is SecretKeyEventContent.SecretStorageKeyPassphrase.Unknown, null ->
                        setOf(SelfVerificationMethod.AesHmacSha2RecoveryKey(keyService, defaultKey.key, content))
                }
                is SecretKeyEventContent.Unknown, null -> setOf()
            }

            return@combine recoveryKeyMethods + deviceVerificationMethod
        }.stateIn(scope)
    }

    fun getActiveUserVerification(
        timelineEvent: TimelineEvent
    ): ActiveUserVerification? {
        return if (isVerificationRequestActive(timelineEvent.event.originTimestamp)) {
            val cache =
                activeUserVerifications.value.find { it.roomId == timelineEvent.roomId && it.relatesTo?.eventId == timelineEvent.eventId }
            if (cache != null) cache
            else {
                val eventContent = timelineEvent.event.content
                val request =
                    if (eventContent is VerificationRequestMessageEventContent) eventContent
                    else {
                        val decryptedEventContent = timelineEvent.decryptedEvent?.getOrNull()?.content
                        if (decryptedEventContent is VerificationRequestMessageEventContent) decryptedEventContent
                        else null
                    }
                val sender = timelineEvent.event.sender
                if (request != null
                    && (request.to == ownUserId || sender == ownUserId && request.fromDevice == ownDeviceId)
                    && request.to != sender
                ) {
                    ActiveUserVerification(
                        request = request,
                        requestIsFromOurOwn = sender == ownUserId,
                        requestEventId = timelineEvent.eventId,
                        requestTimestamp = timelineEvent.event.originTimestamp,
                        ownUserId = ownUserId,
                        ownDeviceId = ownDeviceId,
                        theirUserId = if (sender == ownUserId) request.to else sender,
                        theirInitialDeviceId = if (sender == ownUserId) null else request.fromDevice,
                        roomId = timelineEvent.roomId,
                        supportedMethods = supportedMethods,
                        api = api,
                        store = store,
                        olm = olmService,
                        user = userService,
                        room = roomService,
                        key = keyService,
                    ).also { auv -> activeUserVerifications.update { it + auv } }
                } else null
            }
        } else null
    }
}