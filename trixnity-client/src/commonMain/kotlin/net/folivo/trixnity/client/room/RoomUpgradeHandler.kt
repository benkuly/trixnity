package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.TombstoneEventContent
import net.folivo.trixnity.core.model.events.roomIdOrNull
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.subscribeContent
import net.folivo.trixnity.core.unsubscribeOnCompletion

private val log = KotlinLogging.logger { }

class RoomUpgradeHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val configuration: MatrixClientConfiguration,
) : EventHandler {
    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeContent(subscriber = ::setRoomReplacedBy).unsubscribeOnCompletion(scope)
        api.sync.subscribeContent(subscriber = ::setRoomReplaces).unsubscribeOnCompletion(scope)
        api.sync.subscribe(Priority.AFTER_DEFAULT, ::joinUpgradedRooms).unsubscribeOnCompletion(scope)
    }

    internal suspend fun setRoomReplacedBy(event: ClientEvent<TombstoneEventContent>) {
        val roomId = event.roomIdOrNull
        if (roomId != null) {
            roomStore.update(roomId) {
                it?.copy(nextRoomId = event.content.replacementRoom)
            }
        }
    }

    internal suspend fun setRoomReplaces(event: ClientEvent<CreateEventContent>) {
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
            roomStore.getAll().first().mapValues { it.value.first() }
        allRooms.values.filterNotNull().filter { it.membership == Membership.INVITE }.forEach { room ->
            val previousRoom = room.previousRoomId?.let { allRooms[it] }
            if (previousRoom?.nextRoomId == room.roomId && previousRoom.membership == Membership.JOIN) {
                api.room.joinRoom(room.roomId).onFailure {
                    log.warn(it) { "Failed to automatically join upgraded room. It will be tried again after the next sync." }
                }
            }
        }
    }
}