package net.folivo.trixnity.client.user

import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent

interface LazyMemberEventHandler {
    suspend fun handleLazyMemberEvents(memberEvents: List<StateEvent<MemberEventContent>>)
}