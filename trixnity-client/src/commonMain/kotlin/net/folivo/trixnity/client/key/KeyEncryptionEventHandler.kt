package net.folivo.trixnity.client.key

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import net.folivo.trixnity.core.subscribeEventList
import net.folivo.trixnity.core.unsubscribeOnCompletion
import net.folivo.trixnity.crypto.olm.membershipsAllowedToReceiveKey

private val log = KotlinLogging.logger {}

class KeyEncryptionEventHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStateStore: RoomStateStore,
    private val keyStore: KeyStore,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeEventList(Priority.AFTER_DEFAULT, subscriber = ::updateDeviceKeysFromChangedEncryption)
            .unsubscribeOnCompletion(scope)
    }

    internal suspend fun updateDeviceKeysFromChangedEncryption(events: List<StateEvent<EncryptionEventContent>>) {
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