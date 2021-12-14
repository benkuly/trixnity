package net.folivo.trixnity.client.verification

import com.benasher44.uuid.uuid4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.crypto.OlmService
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
import net.folivo.trixnity.core.model.events.m.key.verification.RequestEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod.Sas
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.VerificationRequestMessageEventContent
import net.folivo.trixnity.olm.OlmLibraryException
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger

class VerificationService(
    private val ownUserId: UserId,
    private val ownDeviceId: String,
    private val api: MatrixApiClient,
    private val store: Store,
    private val olm: OlmService,
    private val room: RoomService,
    private val user: UserService,
    private val supportedMethods: Set<VerificationMethod> = setOf(Sas),
    private val loggerFactory: LoggerFactory
) {
    private val log = newLogger(loggerFactory)

    private val _activeDeviceVerification = MutableStateFlow<ActiveDeviceVerification?>(null)
    val activeDeviceVerification = _activeDeviceVerification.asStateFlow()
    private val activeUserVerifications = MutableStateFlow<List<ActiveUserVerification>>(listOf())

    internal suspend fun start(scope: CoroutineScope) {
        api.sync.subscribe(::handleDeviceVerificationRequestEvents)
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        scope.launch(start = UNDISPATCHED) {
            olm.decryptedOlmEvents.collect(::handleOlmDecryptedDeviceVerificationRequestEvents)
        }
        scope.launch(start = UNDISPATCHED) {
            activeUserVerifications.collect { startLifecycleOfActiveVerifications(it, this) }
        }
        scope.launch(start = UNDISPATCHED) {
            activeDeviceVerification.collect { it?.let { startLifecycleOfActiveVerifications(listOf(it), this) } }
        }
    }

    private suspend fun handleDeviceVerificationRequestEvents(event: Event<RequestEventContent>) {
        val content = event.content
        when (event) {
            is Event.ToDeviceEvent -> {
                if (isVerificationRequestActive(content.timestamp)) {
                    log.info { "got new device verification request from ${event.sender}" }
                    if (_activeDeviceVerification.value != null) {
                        log.info { "already have an active device verification -> cancelling new verification request" }
                        ActiveDeviceVerification(
                            request = event.content,
                            ownUserId = ownUserId,
                            ownDeviceId = ownDeviceId,
                            theirUserId = event.sender,
                            theirDeviceId = content.fromDevice,
                            supportedMethods = supportedMethods,
                            api = api,
                            olm = olm,
                            store = store,
                            loggerFactory = loggerFactory
                        ).cancel()
                    } else {
                        _activeDeviceVerification.value =
                            ActiveDeviceVerification(
                                request = event.content,
                                ownUserId = ownUserId,
                                ownDeviceId = ownDeviceId,
                                theirUserId = event.sender,
                                theirDeviceId = content.fromDevice,
                                supportedMethods = supportedMethods,
                                api = api,
                                olm = olm,
                                store = store,
                                loggerFactory = loggerFactory
                            )
                    }
                }
            }
            else -> log.warning { "got new device verification request with an event type ${event::class.simpleName}, that we did not expected" }
        }
    }

    private suspend fun handleOlmDecryptedDeviceVerificationRequestEvents(event: OlmService.DecryptedOlmEvent) {
        when (val content = event.decrypted.content) {
            is RequestEventContent -> {
                if (isVerificationRequestActive(content.timestamp)) {
                    log.info { "got new device verification request from ${event.decrypted.sender}" }
                    if (_activeDeviceVerification.value != null) {
                        log.info { "already have an active device verification -> cancelling new verification request" }
                        ActiveDeviceVerification(
                            request = content,
                            ownUserId = ownUserId,
                            ownDeviceId = ownDeviceId,
                            theirUserId = event.decrypted.sender,
                            theirDeviceId = content.fromDevice,
                            supportedMethods = supportedMethods,
                            api = api,
                            olm = olm,
                            store = store,
                            loggerFactory = loggerFactory
                        ).cancel()
                    } else {
                        _activeDeviceVerification.value =
                            ActiveDeviceVerification(
                                request = content,
                                ownUserId = ownUserId,
                                ownDeviceId = ownDeviceId,
                                theirUserId = event.decrypted.sender,
                                theirDeviceId = content.fromDevice,
                                supportedMethods = supportedMethods,
                                api = api,
                                olm = olm,
                                store = store,
                                loggerFactory = loggerFactory
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

    suspend fun createDeviceVerificationRequest(theirUserId: UserId, theirDeviceId: String) {
        log.info { "create new device verification request to $theirUserId ($theirDeviceId)" }
        val request = RequestEventContent(
            ownDeviceId, supportedMethods, Clock.System.now().toEpochMilliseconds(), uuid4().toString()
        )
        val encryptedContent = try {
            olm.events.encryptOlm(request, theirUserId, theirDeviceId)
        } catch (olmError: OlmLibraryException) {
            request
        }
        api.users.sendToDevice(mapOf(theirUserId to mapOf(theirDeviceId to encryptedContent)))
        val existingDeviceVerification = _activeDeviceVerification.getAndUpdate {
            ActiveDeviceVerification(
                request = request,
                ownUserId = ownUserId,
                ownDeviceId = ownDeviceId,
                theirUserId = theirUserId,
                theirDeviceId = theirDeviceId,
                supportedMethods = supportedMethods,
                api = api,
                olm = olm,
                store = store,
                loggerFactory = loggerFactory
            )
        }
        existingDeviceVerification?.cancel()
    }

    suspend fun createUserVerificationRequest(theirUserId: UserId) {
        log.info { "create new user verification request to $theirUserId" }
        val request = VerificationRequestMessageEventContent(ownDeviceId, theirUserId, supportedMethods)
        val roomId =
            store.globalAccountData.get<DirectEventContent>()?.content?.mappings?.get(theirUserId)?.firstOrNull()
                ?: api.rooms.createRoom(invite = setOf(theirUserId), isDirect = true)
        val sendContent = try {
            possiblyEncryptEvent(request, roomId, store, olm, user)
        } catch (olmError: OlmLibraryException) {
            request
        }
        api.rooms.sendMessageEvent(roomId, sendContent)
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
                if (request != null) {
                    ActiveUserVerification(
                        request = request,
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
                        olm = olm,
                        user = user,
                        room = room,
                        loggerFactory = loggerFactory
                    ).also { auv -> activeUserVerifications.update { it + auv } }
                } else null
            }
        } else null
    }
}