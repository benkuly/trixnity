package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.MatrixServerException
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.TombstoneEventContent
import net.folivo.trixnity.core.subscribeEventList
import net.folivo.trixnity.core.unsubscribeOnCompletion

private val log = KotlinLogging.logger("net.folivo.trixnity.client.room.RoomUpgradeHandler")

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