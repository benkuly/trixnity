package net.folivo.trixnity.client.store

import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.core.model.MatrixId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership

suspend inline fun <reified C : StateEventContent> Store.RoomsStore.RoomStateStore.byId(roomId: MatrixId.RoomId):
        StateFlow<Map<String, Event<C>>> = this.byId(roomId, C::class)

suspend inline fun <reified C : StateEventContent> Store.RoomsStore.RoomStateStore.byId(
    roomId: MatrixId.RoomId,
    stateKey: String
): StateFlow<Event<C>?> = this.byId(roomId, stateKey, C::class)

// FIXME test
suspend inline fun Store.RoomsStore.RoomStateStore.members(
    roomId: MatrixId.RoomId,
    membership: Membership,
    vararg moreMemberships: Membership
): Set<MatrixId.UserId> =
    byId<MemberEventContent>(roomId).value.filter { entry ->
        (moreMemberships.toList() + membership).map { entry.value.content.membership == it }.find { it } ?: false
    }.map { MatrixId.UserId(it.key) }.toSet()


suspend inline fun Store.RoomsStore.encryptedJoinedRooms(): List<Room> =
    all().value.filter { it.encryptionAlgorithm != null && it.ownMembership == Membership.JOIN }


suspend inline fun Store.RoomsStore.RoomTimelineStore.getNext(event: TimelineEvent): StateFlow<TimelineEvent?>? =
    event.nextEventId?.let { this.byId(it, event.roomId) }

suspend inline fun Store.RoomsStore.RoomTimelineStore.getPrevious(event: TimelineEvent): StateFlow<TimelineEvent?>? =
    event.previousEventId?.let { this.byId(it, event.roomId) }

suspend inline fun Store.DeviceKeysStores.isTracked(userId: MatrixId.UserId): Boolean =
    byUserId(userId).value.isNullOrEmpty()

