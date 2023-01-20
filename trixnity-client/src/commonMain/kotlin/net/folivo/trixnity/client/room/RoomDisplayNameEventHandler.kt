package net.folivo.trixnity.client.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import mu.KotlinLogging
import net.folivo.trixnity.client.getRoomId
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.JoinedRoom.RoomSummary
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.CanonicalAliasEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribe

private val log = KotlinLogging.logger {}

class RoomDisplayNameEventHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(::setRoomDisplayNameFromNameEvent)
        api.sync.subscribe(::setRoomDisplayNameFromCanonicalAliasEvent)
        api.sync.subscribeAfterSyncResponse(::handleSetRoomDisplayNamesQueue)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.unsubscribe(::setRoomDisplayNameFromNameEvent)
            api.sync.unsubscribe(::setRoomDisplayNameFromCanonicalAliasEvent)
            api.sync.unsubscribeAfterSyncResponse(::handleSetRoomDisplayNamesQueue)
        }
    }

    internal fun setRoomDisplayNameFromNameEvent(event: Event<NameEventContent>) {
        val roomId = event.getRoomId()
        if (roomId != null) {
            log.debug { "update room displayname of $roomId due to name event" }
            setRoomDisplayNamesQueue.update {
                if (it.containsKey(roomId)) it else it + (roomId to null)
            }
        }
    }

    internal fun setRoomDisplayNameFromCanonicalAliasEvent(event: Event<CanonicalAliasEventContent>) {
        val roomId = event.getRoomId()
        if (roomId != null) {
            log.debug { "update room displayname of $roomId doe to alias event" }
            setRoomDisplayNamesQueue.update {
                if (it.containsKey(roomId)) it else it + (roomId to null)
            }
        }
    }

    private val setRoomDisplayNamesQueue =
        MutableStateFlow(mapOf<RoomId, RoomSummary?>())

    internal suspend fun handleSetRoomDisplayNamesQueue(syncResponse: Sync.Response) {
        syncResponse.room?.join?.entries?.forEach { room ->
            room.value.summary?.also { roomSummary ->
                setRoomDisplayNamesQueue.update { it + (room.key to roomSummary) }
            }
        }
        setRoomDisplayNamesQueue.value.forEach { (roomId, roomSummary) ->
            setRoomDisplayName(roomId, roomSummary)
        }
        setRoomDisplayNamesQueue.value = mapOf()
    }

    internal suspend fun setRoomDisplayName(
        roomId: RoomId,
        roomSummary: RoomSummary?,
    ) {
        val oldRoomSummary = roomStore.get(roomId).first()?.name?.summary

        if (oldRoomSummary == roomSummary) return

        val mergedRoomSummary = RoomSummary(
            heroes = roomSummary?.heroes ?: oldRoomSummary?.heroes,
            joinedMemberCount = roomSummary?.joinedMemberCount ?: oldRoomSummary?.joinedMemberCount,
            invitedMemberCount = roomSummary?.invitedMemberCount ?: oldRoomSummary?.invitedMemberCount,
        )

        val nameFromNameEvent = roomStateStore.getByStateKey<NameEventContent>(roomId).first()?.content?.name
        val nameFromAliasEvent =
            roomStateStore.getByStateKey<CanonicalAliasEventContent>(roomId).first()?.content?.alias?.full

        val roomName = when {
            nameFromNameEvent.isNullOrEmpty().not() ->
                RoomDisplayName(explicitName = nameFromNameEvent, summary = mergedRoomSummary)

            nameFromAliasEvent.isNullOrEmpty().not() ->
                RoomDisplayName(explicitName = nameFromAliasEvent, summary = mergedRoomSummary)

            else -> {
                val heroes = mergedRoomSummary.heroes
                val joinedMemberCount =
                    mergedRoomSummary.joinedMemberCount ?: roomStateStore.membersCount(roomId, Membership.JOIN)
                val invitedMemberCount =
                    mergedRoomSummary.invitedMemberCount ?: roomStateStore.membersCount(roomId, Membership.INVITE)
                val us = 1

                log.debug { "calculate room display name of $roomId (heroes=$heroes, joinedMemberCount=$joinedMemberCount, invitedMemberCount=$invitedMemberCount)" }

                if (joinedMemberCount + invitedMemberCount <= 1) {
                    // the room contains us or nobody
                    when {
                        heroes.isNullOrEmpty() -> RoomDisplayName(isEmpty = true, summary = mergedRoomSummary)
                        else -> {
                            val isCompletelyEmpty = joinedMemberCount + invitedMemberCount <= 0
                            val leftMembersCount =
                                roomStateStore.membersCount(
                                    roomId,
                                    Membership.LEAVE,
                                    Membership.BAN
                                ) - if (isCompletelyEmpty) us else 0
                            when {
                                leftMembersCount <= heroes.size ->
                                    RoomDisplayName(
                                        isEmpty = true,
                                        summary = mergedRoomSummary
                                    )

                                else -> {
                                    RoomDisplayName(
                                        isEmpty = true,
                                        otherUsersCount = leftMembersCount - heroes.size,
                                        summary = mergedRoomSummary
                                    )
                                }
                            }
                        }
                    }
                } else {
                    when {
                        //case ist not specified in the Spec, so this catches server misbehavior
                        heroes.isNullOrEmpty() ->
                            RoomDisplayName(
                                otherUsersCount = joinedMemberCount + invitedMemberCount - us,
                                summary = mergedRoomSummary
                            )

                        joinedMemberCount + invitedMemberCount - us <= heroes.size ->
                            RoomDisplayName(
                                summary = mergedRoomSummary
                            )

                        else ->
                            RoomDisplayName(
                                otherUsersCount = joinedMemberCount + invitedMemberCount - heroes.size - us,
                                summary = mergedRoomSummary
                            )
                    }
                }
            }
        }
        roomStore.update(roomId) { oldRoom ->
            oldRoom?.copy(name = roomName)
                ?: Room(roomId = roomId, name = roomName)
        }
    }
}