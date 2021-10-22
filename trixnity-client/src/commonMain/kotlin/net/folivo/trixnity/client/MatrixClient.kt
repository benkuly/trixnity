package net.folivo.trixnity.client

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.authentication.IdentifierType
import net.folivo.trixnity.client.api.authentication.LoginType
import net.folivo.trixnity.client.api.users.Filters
import net.folivo.trixnity.client.api.users.RoomFilter
import net.folivo.trixnity.client.crypto.OlmManager
import net.folivo.trixnity.client.media.MediaManager
import net.folivo.trixnity.client.room.RoomManager
import net.folivo.trixnity.client.room.outbox.OutboxMessageMediaUploaderMapping
import net.folivo.trixnity.client.store.SecureStore
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.StoreFactory
import net.folivo.trixnity.client.user.UserManager
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


// TODO test
class MatrixClient private constructor(
    private val store: Store,
    val api: MatrixApiClient,
    val olm: OlmManager,
    val room: RoomManager,
    val user: UserManager,
    val media: MediaManager,
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
            customOutboxMessageMediaUploaderMappings: Set<OutboxMessageMediaUploaderMapping<*>> = setOf(),
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
            api.accessToken = newAccessToken
            store.account.accessToken.value = newAccessToken
            store.account.userId.value = userId
            store.account.deviceId.value = deviceId
            val olm = OlmManager(
                store = store,
                secureStore = secureStore,
                api = api,
                json = api.json,
                loggerFactory = loggerFactory
            )
            store.deviceKeys.update(userId) { mapOf(deviceId to olm.myDeviceKeys.signed) }
            api.keys.uploadKeys(deviceKeys = olm.myDeviceKeys)

            val mediaManager = MediaManager(api, store, loggerFactory)
            val userManager = UserManager(store, api, loggerFactory)
            val roomManager =
                RoomManager(
                    store,
                    api,
                    olm,
                    userManager,
                    mediaManager,
                    customOutboxMessageMediaUploaderMappings,
                    loggerFactory
                )

            return MatrixClient(store, api, olm, roomManager, userManager, mediaManager, loggerFactory)
        }

        suspend fun fromStore(
            hostname: String,
            port: Int = 443,
            secure: Boolean = true,
            storeFactory: StoreFactory,
            secureStore: SecureStore,
            customMappings: EventContentSerializerMappings? = null,
            customOutboxMessageMediaUploaderMappings: Set<OutboxMessageMediaUploaderMapping<*>> = setOf(),
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
                api.accessToken = accessToken
                val olm = OlmManager(
                    store = store,
                    secureStore = secureStore,
                    api = api,
                    json = api.json,
                    loggerFactory = loggerFactory
                )
                val mediaManager = MediaManager(api, store, loggerFactory)
                val userManager = UserManager(store, api, loggerFactory)
                val roomManager =
                    RoomManager(
                        store,
                        api,
                        olm,
                        userManager,
                        mediaManager,
                        customOutboxMessageMediaUploaderMappings,
                        loggerFactory
                    )

                MatrixClient(store, api, olm, roomManager, userManager, mediaManager, loggerFactory)
            } else null
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    val isLoggedIn = store.account.accessToken.map { it != null }.stateIn(scope, Eagerly, false)

    val syncState = api.sync.currentSyncState

    val userId = store.account.userId.value ?: throw IllegalArgumentException("userId must not be null")

    suspend fun logout() {
        api.sync.stop()
        api.authentication.logout()
        olm.free()
    }

    suspend fun startSync() {
        val handler = CoroutineExceptionHandler { _, exception ->
            // TODO maybe log to some sort of backend
            log.error(exception) { "There was an unexpected exception with handling sync data. Will cancel sync now. This should never happen!!!" }
            scope.launch { api.sync.cancel() }
        }
        scope.launch(handler) {
            launch { olm.startEventHandling() }
            launch { room.startEventHandling() }
            launch { user.startEventHandling() }
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
        )
    }
}