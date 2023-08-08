package net.folivo.trixnity.client.utils

import io.ktor.util.reflect.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.EventContent

internal inline fun <reified T : EventContent> Sync.Response.filter(): Flow<Event<T>> = flow {
    toDevice?.events?.forEach { emit(it) }
    accountData?.events?.forEach { emit(it) }
    presence?.events?.forEach { emit(it) }
    room?.join?.forEach { (_, joinedRoom) ->
        joinedRoom.state?.events?.forEach { emit(it) }
        joinedRoom.timeline?.events?.forEach { emit(it) }
        joinedRoom.ephemeral?.events?.forEach { emit(it) }
        joinedRoom.accountData?.events?.forEach { emit(it) }
    }
    room?.invite?.forEach { (_, invitedRoom) ->
        invitedRoom.inviteState?.events?.forEach { emit(it) }
    }
    room?.knock?.forEach { (_, invitedRoom) ->
        invitedRoom.knockState?.events?.forEach { emit(it) }
    }
    room?.leave?.forEach { (_, leftRoom) ->
        leftRoom.state?.events?.forEach { emit(it) }
        leftRoom.timeline?.events?.forEach { emit(it) }
        leftRoom.accountData?.events?.forEach { emit(it) }
    }
}.filter { it.content.instanceOf(T::class) }
    .filterIsInstance<Event<T>>()