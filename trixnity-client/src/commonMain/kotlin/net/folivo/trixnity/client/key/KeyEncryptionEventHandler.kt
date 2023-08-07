package net.folivo.trixnity.client.key

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.utils.filter
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import net.folivo.trixnity.crypto.olm.membershipsAllowedToReceiveKey

private val log = KotlinLogging.logger {}

class KeyEncryptionEventHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStateStore: RoomStateStore,
    private val keyStore: KeyStore,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.afterSyncResponse.subscribe(::handleSyncResponse)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.afterSyncResponse.unsubscribe(::handleSyncResponse)
        }
    }

    internal suspend fun handleSyncResponse(syncResponse: Sync.Response) {
        updateDeviceKeysFromChangedEncryption(
            syncResponse.filter<EncryptionEventContent>()
                .filterIsInstance<Event.StateEvent<EncryptionEventContent>>()
                .toList()
        )
    }

    internal suspend fun updateDeviceKeysFromChangedEncryption(events: List<Event.StateEvent<EncryptionEventContent>>) {
        log.debug { "update device keys from changed encryption" }
        val outdatedKeys = events.map { event ->
            val allowedMemberships =
                roomStateStore.getByStateKey<HistoryVisibilityEventContent>(event.roomId)
                    .first()?.content?.historyVisibility
                    .membershipsAllowedToReceiveKey
            val outdatedKeys = roomStateStore.members(event.roomId, allowedMemberships).filterNot {
                keyStore.isTracked(it)
            }
            outdatedKeys
        }.flatten()
        keyStore.updateOutdatedKeys { it + outdatedKeys }
    }
}