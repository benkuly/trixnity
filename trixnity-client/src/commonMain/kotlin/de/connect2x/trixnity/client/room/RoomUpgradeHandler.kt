package de.connect2x.trixnity.client.room

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.warn
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.store.RoomStateStore
import de.connect2x.trixnity.client.store.RoomStore
import de.connect2x.trixnity.client.store.getByStateKey
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.core.ClientEventEmitter.Priority
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.MatrixServerException
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.room.TombstoneEventContent
import de.connect2x.trixnity.core.subscribeEventList
import de.connect2x.trixnity.core.unsubscribeOnCompletion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val log = Logger("de.connect2x.trixnity.client.room.RoomUpgradeHandler")

class RoomUpgradeHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val configuration: MatrixClientConfiguration,
) : EventHandler {
    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeEventList(Priority.AFTER_DEFAULT, ::onTombstone).unsubscribeOnCompletion(scope)
        api.sync.subscribeEventList(Priority.AFTER_DEFAULT, ::onCreate).unsubscribeOnCompletion(scope)
        scope.launch { joinUpgradedRooms() }
    }

    private val tombstonesFromSync = MutableStateFlow<Set<RoomId>>(setOf())

    internal fun onTombstone(tombstones: List<ClientEvent.RoomEvent.StateEvent<TombstoneEventContent>>) {
        if (configuration.autoJoinUpgradedRooms.not() || tombstones.isEmpty()) return

        tombstonesFromSync.update { it + tombstones.map { it.roomId }.toSet() }
    }

    internal fun onCreate(creates: List<ClientEvent.RoomEvent.StateEvent<CreateEventContent>>) {
        if (configuration.autoJoinUpgradedRooms.not() || creates.isEmpty()) return

        tombstonesFromSync.update { it + creates.mapNotNull { it.content.predecessor?.roomId }.toSet() }
    }

    private val debounce = 10.seconds
    private val initialDelay = 5.minutes

    @OptIn(FlowPreview::class)
    internal suspend fun joinUpgradedRooms() {
        if (configuration.autoJoinUpgradedRooms.not()) return
        // if we missend tombstones due to a stopped MatrixClient, we want at least check once the MatrixClient is running for a while
        coroutineScope {
            select {
                launch {
                    tombstonesFromSync.debounce(debounce).first { it.isNotEmpty() }
                }.onJoin { }
                launch {
                    delay(initialDelay)

                }.onJoin { }
            }.also {
                currentCoroutineContext().cancelChildren()
            }
        }
        val upgradedRooms = roomStore.getAll().flattenValues().first()
            .filter { it.nextRoomId != null && it.membership == Membership.JOIN }
            .map { it.roomId }.toSet()
        joinUpgradedRooms(upgradedRooms)
        while (currentCoroutineContext().isActive) {
            val upgradedRooms = tombstonesFromSync.debounce(debounce).first { it.isNotEmpty() }
            joinUpgradedRooms(upgradedRooms)
        }
    }

    private suspend fun joinUpgradedRooms(roomIds: Set<RoomId>) {
        log.debug { "joining upgraded rooms" }
        for (roomId in roomIds) {
            val tombstone = roomStateStore.getByStateKey<TombstoneEventContent>(roomId).first() ?: continue
            val nextRoomId = tombstone.content.replacementRoom
            val nextRoom = roomStore.get(nextRoomId).first()
            when {
                nextRoom == null -> {
                    log.debug { "ignore upgrade room $roomId -> $nextRoomId because invite missing" }
                    tombstonesFromSync.update { it - roomId }
                }

                nextRoom.membership == Membership.INVITE -> {
                    api.room.joinRoom(nextRoomId).fold(
                        onSuccess = {
                            log.debug { "joined upgraded invited room $roomId -> $nextRoomId" }
                            tombstonesFromSync.update { it - roomId }
                        },
                        onFailure = {
                            log.warn(it) { "could not join upgraded invited room $roomId -> $nextRoomId" }
                            if (it is MatrixServerException && it.statusCode.value in (400 until 500)) {
                                tombstonesFromSync.update { it - roomId }
                            }
                        }
                    )
                }

                else -> {
                    log.debug { "ignore upgrade room $roomId -> $nextRoomId because membership is ${nextRoom.membership}" }
                    tombstonesFromSync.update { it - roomId }
                }
            }
        }
    }
}
