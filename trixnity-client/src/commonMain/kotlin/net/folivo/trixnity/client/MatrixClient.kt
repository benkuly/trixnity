package net.folivo.trixnity.client

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.authentication.IdentifierType
import net.folivo.trixnity.client.api.authentication.LoginType
import net.folivo.trixnity.client.crypto.OlmManager
import net.folivo.trixnity.client.room.RoomManager
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger


// TODO test
class MatrixClient private constructor(
    private val store: Store,
    val api: MatrixApiClient,
    val olm: OlmManager,
    val rooms: RoomManager,
    private val loggerFactory: LoggerFactory
) {

    private val log = newLogger(loggerFactory)

    companion object {
        suspend fun login(
            identifier: IdentifierType,
            password: String,
            initialDeviceDisplayName: String? = null,
            store: Store,
            loggerFactory: LoggerFactory = LoggerFactory.default
        ): MatrixClient {
            val api = MatrixApiClient(
                store.server.hostname,
                store.server.port,
                store.server.secure,
                store.account.accessToken,
                loggerFactory = loggerFactory
            )
            val (userId, accessToken, deviceId) = api.authentication.login(
                identifier = identifier,
                passwordOrToken = password,
                type = LoginType.Password,
                initialDeviceDisplayName = initialDeviceDisplayName
            )
            store.account.accessToken.value = accessToken
            store.account.userId.value = userId
            store.account.deviceId.value = deviceId
            val roomManager = RoomManager(store, api)
            val olm = OlmManager(
                store = store,
                api = api,
                json = api.json,
                roomManager = roomManager,
                loggerFactory = loggerFactory
            )
            store.deviceKeys.byUserId(userId).value = mapOf(deviceId to olm.myDeviceKeys.signed)
            api.keys.uploadKeys(deviceKeys = olm.myDeviceKeys)

            return MatrixClient(store, api, olm, roomManager, loggerFactory)
        }

        fun fromStore(
            store: Store,
            loggerFactory: LoggerFactory = LoggerFactory.default
        ): MatrixClient? {
            val api = MatrixApiClient(
                store.server.hostname,
                store.server.port,
                store.server.secure,
                store.account.accessToken,
            )

            val accessToken = store.account.accessToken.value
            val userId = store.account.userId.value
            val deviceId = store.account.deviceId.value

            return if (accessToken != null && userId != null && deviceId != null) {
                val roomManager = RoomManager(store, api)
                val olm = OlmManager(
                    store = store,
                    api = api,
                    json = api.json,
                    roomManager = roomManager,
                    loggerFactory = loggerFactory
                )
                MatrixClient(store, api, olm, roomManager, loggerFactory)
            } else null
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    suspend fun isLoggedIn(): StateFlow<Boolean> = store.account.accessToken.map { it != null }.stateIn(scope)

    val syncState = api.sync.currentSyncState

    suspend fun logout() {
        api.sync.stop()
        api.authentication.logout()
        olm.free()
        store.clear()
    }


    suspend fun startSync() {
        val handler = CoroutineExceptionHandler { _, exception ->
            // TODO maybe log to some sort of backend
            log.error(exception) { "There was an unexpected exception with handling sync data. Will cancel sync now." }
            scope.launch { api.sync.cancel() }
        }
        scope.launch(handler) {
            launch {
                olm.startEventHandling()
            }
            launch {
                rooms.startEventHandling()
            }
        }

        api.sync.start(
            filter = store.account.filterId.value,
            setPresence = PresenceEventContent.Presence.ONLINE,
            currentBatchToken = store.account.syncBatchToken,
        )
    }
}