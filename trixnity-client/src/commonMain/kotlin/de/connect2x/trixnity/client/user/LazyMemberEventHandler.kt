package de.connect2x.trixnity.client.user

import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent

interface LazyMemberEventHandler {
    suspend fun handleLazyMemberEvents(memberEvents: List<StateEvent<MemberEventContent>>)
}