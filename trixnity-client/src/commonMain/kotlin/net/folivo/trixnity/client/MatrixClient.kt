package net.folivo.trixnity.client

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.authentication.IdentifierType
import net.folivo.trixnity.client.api.authentication.LoginType
import net.folivo.trixnity.client.api.users.Filters
import net.folivo.trixnity.client.api.users.RoomFilter
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.room.outbox.OutboxMessageMediaUploaderMapping
import net.folivo.trixnity.client.store.SecureStore
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoreFactory
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.client.verification.VerificationService
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


// TODO test
class MatrixClient private constructor(
    private val store: Store,
    val api: MatrixApiClient,
    val olm: OlmService,
    val room: RoomService,
    val user: UserService,
    val media: MediaService,
    val verification: VerificationService,
    private val scope: CoroutineScope,
    loggerFactory: LoggerFactory
) {

    private val log = newLogger(loggerFactory)

    companion object {
        suspend fun login(
            hostname: String,
            port: Int = 443,
            secure: Boolean = true,
            identifier: IdentifierType,
            password: String,
            initialDeviceDisplayName: String? = null,
            storeFactory: StoreFactory,
            secureStore: SecureStore,
            customMappings: EventContentSerializerMappings? = null,
            setOwnMessagesAsFullyRead: Boolean = false,
            customOutboxMessageMediaUploaderMappings: Set<OutboxMessageMediaUploaderMapping<*>> = setOf(),
            scope: CoroutineScope,
            loggerFactory: LoggerFactory = LoggerFactory.default
        ): MatrixClient {
            val api = MatrixApiClient(
                hostname,
                port,
                secure,
                customMappings = customMappings,
                loggerFactory = loggerFactory
            )
            val store =
                storeFactory.createStore(api.eventContentSerializerMappings, api.json, loggerFactory = loggerFactory)
            store.init()

            val (userId, newAccessToken, deviceId) = api.authentication.login(
                identifier = identifier,
                passwordOrToken = password,
                type = LoginType.Password,
                initialDeviceDisplayName = initialDeviceDisplayName
            )
            api.accessToken.value = newAccessToken
            store.account.accessToken.value = newAccessToken
            store.account.userId.value = userId
            store.account.deviceId.value = deviceId
            val olmService = OlmService(
                store = store,
                secureStore = secureStore,
                api = api,
                json = api.json,
                loggerFactory = loggerFactory
            )
            store.deviceKeys.update(userId) { mapOf(deviceId to olmService.myDeviceKeys.signed) }
            api.keys.uploadKeys(deviceKeys = olmService.myDeviceKeys)

            val mediaService = MediaService(api, store, loggerFactory)
            val userService = UserService(store, api, loggerFactory)
            val roomService =
                RoomService(
                    store,
                    api,
                    olmService,
                    userService,
                    mediaService,
                    setOwnMessagesAsFullyRead,
                    customOutboxMessageMediaUploaderMappings,
                    loggerFactory
                )
            val verificationService = VerificationService(
                ownUserId = store.account.userId.value ?: throw IllegalArgumentException("userId must not be null"),
                ownDeviceId = store.account.deviceId.value ?: throw IllegalArgumentException("userId must not be null"),
                api = api,
                store = store,
                olm = olmService,
                room = roomService,
                user = userService,
                loggerFactory = loggerFactory
            )

            return MatrixClient(
                store,
                api,
                olmService,
                roomService,
                userService,
                mediaService,
                verificationService,
                scope,
                loggerFactory
            )
        }

        suspend fun fromStore(
            hostname: String,
            port: Int = 443,
            secure: Boolean = true,
            storeFactory: StoreFactory,
            secureStore: SecureStore,
            customMappings: EventContentSerializerMappings? = null,
            setOwnMessagesAsFullyRead: Boolean = false,
            customOutboxMessageMediaUploaderMappings: Set<OutboxMessageMediaUploaderMapping<*>> = setOf(),
            scope: CoroutineScope,
            loggerFactory: LoggerFactory = LoggerFactory.default
        ): MatrixClient? {
            val api = MatrixApiClient(
                hostname,
                port,
                secure,
                customMappings = customMappings,
                loggerFactory = loggerFactory
            )
            val store =
                storeFactory.createStore(api.eventContentSerializerMappings, api.json, loggerFactory = loggerFactory)
            store.init()

            val accessToken = store.account.accessToken.value
            val userId = store.account.userId.value
            val deviceId = store.account.deviceId.value

            return if (accessToken != null && userId != null && deviceId != null) {
                api.accessToken.value = accessToken
                val olmService = OlmService(
                    store = store,
                    secureStore = secureStore,
                    api = api,
                    json = api.json,
                    loggerFactory = loggerFactory
                )
                val mediaService = MediaService(api, store, loggerFactory)
                val userService = UserService(store, api, loggerFactory)
                val roomService = RoomService(
                    store,
                    api,
                    olmService,
                    userService,
                    mediaService,
                    setOwnMessagesAsFullyRead,
                    customOutboxMessageMediaUploaderMappings,
                    loggerFactory
                )
                val verificationService = VerificationService(
                    ownUserId = store.account.userId.value ?: throw IllegalArgumentException("userId must not be null"),
                    ownDeviceId = store.account.deviceId.value
                        ?: throw IllegalArgumentException("userId must not be null"),
                    api = api,
                    store = store,
                    olm = olmService,
                    room = roomService,
                    user = userService,
                    loggerFactory = loggerFactory
                )

                MatrixClient(
                    store,
                    api,
                    olmService,
                    roomService,
                    userService,
                    mediaService,
                    verificationService,
                    scope,
                    loggerFactory
                )
            } else null
        }
    }

    fun isLoggedIn(scope: CoroutineScope): StateFlow<Boolean> {
        return store.account.accessToken.map { it != null }.stateIn(scope, Eagerly, false)
    }

    val syncState = api.sync.currentSyncState

    val userId = store.account.userId.value ?: throw IllegalArgumentException("userId must not be null")

    suspend fun logout() {
        api.sync.stop()
        api.authentication.logout()
        olm.free()
    }

    suspend fun stopSync() {
        api.sync.stop()
    }

    suspend fun startSync() {
        val handler = CoroutineExceptionHandler { _, exception ->
            // TODO maybe log to some sort of backend
            log.error(exception) { "There was an unexpected exception with handling sync data. Will cancel sync now. This should never happen!!!" }
            scope.launch { api.sync.stop() }
        }
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        scope.launch(handler, start = UNDISPATCHED) {
            launch(start = UNDISPATCHED) { olm.startEventHandling() }
            launch(start = UNDISPATCHED) { room.startEventHandling() }
            launch(start = UNDISPATCHED) { user.startEventHandling() }
            launch(start = UNDISPATCHED) { verification.startEventHandling() }
        }

        val myUserId = store.account.userId.value
        requireNotNull(myUserId)
        val filterId = store.account.filterId.value
        if (filterId == null)
            store.account.filterId.value = api.users.setFilter(
                myUserId,
                Filters(room = RoomFilter(state = RoomFilter.StateFilter(lazyLoadMembers = true)))
            )

        api.sync.start(
            filter = store.account.filterId.value,
            setPresence = PresenceEventContent.Presence.ONLINE,
            currentBatchToken = store.account.syncBatchToken,
            scope = scope,
        )
    }
}