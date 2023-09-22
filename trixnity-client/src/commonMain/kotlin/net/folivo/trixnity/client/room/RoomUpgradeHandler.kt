package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.*
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.TombstoneEventContent
import net.folivo.trixnity.core.model.events.roomIdOrNull

private val log = KotlinLogging.logger { }

class RoomUpgradeHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val configuration: MatrixClientConfiguration,
) : EventHandler {
    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeContent(subscriber = ::setRoomReplacedBy).unsubscribeOnCompletion(scope)
        api.sync.subscribeContent(subscriber = ::setRoomReplaces).unsubscribeOnCompletion(scope)
        api.sync.subscribe(EventEmitter.Priority.AFTER_DEFAULT, ::joinUpgradedRooms).unsubscribeOnCompletion(scope)
    }

    internal suspend fun setRoomReplacedBy(event: Event<TombstoneEventContent>) {
        val roomId = event.roomIdOrNull
        if (roomId != null) {
            roomStore.update(roomId) {
                it?.copy(nextRoomId = event.content.replacementRoom)
            }
        }
    }

    internal suspend fun setRoomReplaces(event: Event<CreateEventContent>) {
        val roomId = event.roomIdOrNull
        val predecessor = event.content.predecessor
        if (roomId != null && predecessor != null) {
            roomStore.update(roomId) {
                it?.copy(previousRoomId = predecessor.roomId)
            }
        }
    }

    internal suspend fun joinUpgradedRooms() {
        if (configuration.autoJoinUpgradedRooms.not()) return

        val allRooms =
            roomStore.getAll().value.mapValues { it.value.value }
        allRooms.values.filterNotNull().filter { it.membership == Membership.INVITE }.forEach { room ->
            val previousRoom = room.previousRoomId?.let { allRooms[it] }
            if (previousRoom?.nextRoomId == room.roomId && previousRoom.membership == Membership.JOIN) {
                api.rooms.joinRoom(room.roomId).onFailure {
                    log.warn(it) { "Failed to automatically join upgraded room. It will be tried again after the next sync." }
                }
            }
        }
    }
}