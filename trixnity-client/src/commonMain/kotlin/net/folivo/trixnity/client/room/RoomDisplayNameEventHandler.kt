package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncEvents
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.JoinedRoom.RoomSummary
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.room.CanonicalAliasEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.model.events.roomIdOrNull
import net.folivo.trixnity.core.subscribeContent
import net.folivo.trixnity.core.unsubscribeOnCompletion

private val log = KotlinLogging.logger {}

// TODO merge into RoomListHandler (performance reasons)
class RoomDisplayNameEventHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeContent(subscriber = ::setRoomDisplayNameFromNameEvent).unsubscribeOnCompletion(scope)
        api.sync.subscribeContent(subscriber = ::setRoomDisplayNameFromCanonicalAliasEvent)
            .unsubscribeOnCompletion(scope)
        api.sync.subscribe(Priority.AFTER_DEFAULT, ::handleSetRoomDisplayNamesQueue).unsubscribeOnCompletion(scope)
    }

    internal data class RoomDisplayNameChange(
        val nameEventContent: NameEventContent? = null,
        val canonicalAliasEventContent: CanonicalAliasEventContent? = null,
        val roomSummary: RoomSummary? = null,
    ) {
        fun onlyRoomSummarySet() = nameEventContent == null && canonicalAliasEventContent == null
    }

    private val setRoomDisplayNamesQueue =
        MutableStateFlow(mapOf<RoomId, RoomDisplayNameChange>())

    internal fun setRoomDisplayNameFromNameEvent(event: ClientEvent<NameEventContent>) {
        val roomId = event.roomIdOrNull
        if (roomId != null) {
            log.debug { "update room displayname of $roomId due to name event" }
            setRoomDisplayNamesQueue.update {
                it + (roomId to (
                        it[roomId]?.copy(nameEventContent = event.content)
                            ?: RoomDisplayNameChange(nameEventContent = event.content)
                        ))
            }
        }
    }

    internal fun setRoomDisplayNameFromCanonicalAliasEvent(event: ClientEvent<CanonicalAliasEventContent>) {
        val roomId = event.roomIdOrNull
        if (roomId != null) {
            log.debug { "update room displayname of $roomId doe to alias event" }
            setRoomDisplayNamesQueue.update {
                it + (roomId to (
                        it[roomId]?.copy(canonicalAliasEventContent = event.content)
                            ?: RoomDisplayNameChange(canonicalAliasEventContent = event.content)
                        ))
            }
        }
    }

    internal suspend fun handleSetRoomDisplayNamesQueue(syncEvents: SyncEvents) {
        syncEvents.syncResponse.room?.join?.entries?.forEach { (roomId, room) ->
            room.summary?.also { roomSummary ->
                setRoomDisplayNamesQueue.update {
                    it + (roomId to (
                            it[roomId]?.copy(roomSummary = roomSummary)
                                ?: RoomDisplayNameChange(roomSummary = roomSummary)
                            ))
                }
            }
        }
        setRoomDisplayNamesQueue.value.forEach { (roomId, roomDisplayNameChange) ->
            setRoomDisplayName(roomId, roomDisplayNameChange)
        }
        setRoomDisplayNamesQueue.value = mapOf()
    }

    internal suspend fun setRoomDisplayName(
        roomId: RoomId,
        roomDisplayNameChange: RoomDisplayNameChange,
    ) {
        val roomSummary = roomDisplayNameChange.roomSummary
        val oldRoomSummary = roomStore.get(roomId).first()?.name?.summary

        if (roomDisplayNameChange.onlyRoomSummarySet() && roomSummary == oldRoomSummary) return

        val mergedRoomSummary =
            if (roomSummary == null && roomSummary == oldRoomSummary) null
            else RoomSummary(
                heroes = roomSummary?.heroes ?: oldRoomSummary?.heroes,
                joinedMemberCount = roomSummary?.joinedMemberCount ?: oldRoomSummary?.joinedMemberCount,
                invitedMemberCount = roomSummary?.invitedMemberCount ?: oldRoomSummary?.invitedMemberCount,
            )

        val nameFromNameEvent = roomDisplayNameChange.nameEventContent?.name
            ?: roomStateStore.getByStateKey<NameEventContent>(roomId).first()?.content?.name
        val nameFromAliasEvent = roomDisplayNameChange.canonicalAliasEventContent?.alias?.full
            ?: roomStateStore.getByStateKey<CanonicalAliasEventContent>(roomId).first()?.content?.alias?.full

        val roomName = when {
            nameFromNameEvent.isNullOrEmpty().not() ->
                RoomDisplayName(explicitName = nameFromNameEvent, summary = mergedRoomSummary)

            nameFromAliasEvent.isNullOrEmpty().not() ->
                RoomDisplayName(explicitName = nameFromAliasEvent, summary = mergedRoomSummary)

            else -> {
                val heroes = mergedRoomSummary?.heroes
                val joinedMemberCount = mergedRoomSummary?.joinedMemberCount
                val invitedMemberCount = mergedRoomSummary?.invitedMemberCount
                if (heroes == null || joinedMemberCount == null || invitedMemberCount == null) {
                    log.debug { "calculate room display name cancelled, because there are missing information (e.g. due to an invite)" }
                    return
                }
                val us = 1

                log.debug { "calculate room display name of $roomId (heroes=$heroes, joinedMemberCount=$joinedMemberCount, invitedMemberCount=$invitedMemberCount)" }

                if (joinedMemberCount + invitedMemberCount <= 1) {
                    // the room contains us or nobody
                    when {
                        heroes.isEmpty() -> RoomDisplayName(isEmpty = true, summary = mergedRoomSummary)
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
                        heroes.isEmpty() ->
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