package net.folivo.trixnity.client.utils

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.StateEventContent

internal suspend fun Sync.Response.filterStateEventContent(): Flow<Event<StateEventContent>> = flow {
    room?.join?.forEach { (_, joinedRoom) ->
        joinedRoom.state?.events?.forEach { emit(it) }
        joinedRoom.timeline?.events?.filter { it.content is StateEventContent }
            ?.filterIsInstance<Event<StateEventContent>>()?.forEach { emit(it) }
    }
    room?.invite?.forEach { (_, invitedRoom) ->
        invitedRoom.inviteState?.events?.forEach { emit(it) }
    }
    room?.knock?.forEach { (_, invitedRoom) ->
        invitedRoom.knockState?.events?.forEach { emit(it) }
    }
    room?.leave?.forEach { (_, leftRoom) ->
        leftRoom.state?.events?.forEach { emit(it) }
        leftRoom.timeline?.events?.filter { it.content is StateEventContent }
            ?.filterIsInstance<Event<StateEventContent>>()?.forEach { emit(it) }
    }
}.filterIsInstance<Event<StateEventContent>>()