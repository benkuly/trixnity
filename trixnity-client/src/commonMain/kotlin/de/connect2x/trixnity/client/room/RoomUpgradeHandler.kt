package de.connect2x.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.store.RoomStore
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.core.ClientEventEmitter.Priority
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.MatrixServerException
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.room.TombstoneEventContent
import de.connect2x.trixnity.core.subscribeEventList
import de.connect2x.trixnity.core.unsubscribeOnCompletion

private val log = KotlinLogging.logger("de.connect2x.trixnity.client.room.RoomUpgradeHandler")

class RoomUpgradeHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val configuration: MatrixClientConfiguration,
) : EventHandler {
    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeEventList(Priority.AFTER_DEFAULT, ::joinUpgradedRooms).unsubscribeOnCompletion(scope)
    }

    internal suspend fun joinUpgradedRooms(tombstones: List<ClientEvent.RoomEvent.StateEvent<TombstoneEventContent>>) {
        if (configuration.autoJoinUpgradedRooms.not() || tombstones.isEmpty()) return

        for (tombstone in tombstones) {
            val upgradedRoomId = tombstone.content.replacementRoom
            val room = roomStore.get(upgradedRoomId).first()
            if (room != null && room.membership != Membership.INVITE) {
                log.debug { "skip join of upgraded room, because it is already known and not invited" }
                continue
            }
            api.room.joinRoom(upgradedRoomId).onFailure {
                if (it !is MatrixServerException) throw it
                log.warn(it) { "Failed to automatically join upgraded room. It will be tried again in the next sync or when iterating through timeline." }
            }
        }
    }
}