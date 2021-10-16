package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.core.model.MatrixId.RoomId
import net.folivo.trixnity.core.model.MatrixId.UserId
import net.folivo.trixnity.core.model.crypto.Key
import net.folivo.trixnity.core.model.events.AccountDataEventContent
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership

suspend inline fun <reified C : StateEventContent> RoomStateStore.get(
    roomId: RoomId,
    scope: CoroutineScope? = null
): StateFlow<Map<String, Event<C>>?> = get(roomId, C::class, scope)

suspend inline fun <reified C : StateEventContent> RoomStateStore.getByStateKey(
    roomId: RoomId,
    stateKey: String = "",
    scope: CoroutineScope
): StateFlow<Event<C>?> = getByStateKey(roomId, stateKey, C::class, scope)

suspend inline fun <reified C : StateEventContent> RoomStateStore.getByStateKey(
    roomId: RoomId,
    stateKey: String = ""
): Event<C>? = getByStateKey(roomId, stateKey, C::class)

suspend inline fun <reified C: AccountDataEventContent> RoomAccountDataStore.get(
    roomId: RoomId,
    scope: CoroutineScope? = null
): StateFlow<Event.AccountDataEvent<C>?> = get(roomId, C::class, scope)

// TODO test
suspend inline fun RoomStateStore.members(
    roomId: RoomId,
    membership: Membership,
    vararg moreMemberships: Membership
): Set<UserId> =
    get<MemberEventContent>(roomId).value
        ?.filter { entry ->
            (moreMemberships.toList() + membership).map { entry.value.content.membership == it }.find { it } ?: false
        }?.map { UserId(it.key) }?.toSet() ?: setOf()

// TODO test
suspend inline fun RoomStateStore.membersCount(
    roomId: RoomId,
    membership: Membership,
    vararg moreMemberships: Membership
): Int = get<MemberEventContent>(roomId).value
    ?.filter { entry ->
        (moreMemberships.toList() + membership).map { entry.value.content.membership == it }.find { it } ?: false
    }?.count() ?: 0

fun RoomStore.encryptedJoinedRooms(): List<RoomId> =
    getAll().value.filter { it.encryptionAlgorithm != null && it.membership == Membership.JOIN }.map { it.roomId }

suspend inline fun RoomTimelineStore.getNext(
    event: TimelineEvent,
    scope: CoroutineScope? = null
): StateFlow<TimelineEvent?>? =
    event.nextEventId?.let { get(it, event.roomId, scope) }

suspend inline fun RoomTimelineStore.getPrevious(
    event: TimelineEvent,
    scope: CoroutineScope? = null
): StateFlow<TimelineEvent?>? =
    event.previousEventId?.let { get(it, event.roomId, scope) }

suspend inline fun DeviceKeysStore.isTracked(userId: UserId): Boolean =
    get(userId).value.isNullOrEmpty().not()

suspend inline fun OlmStore.waitForInboundMegolmSession(
    roomId: RoomId,
    sessionId: String,
    senderKey: Key.Curve25519Key,
    scope: CoroutineScope
) {
    getInboundMegolmSession(senderKey, sessionId, roomId, scope).first { it != null }
}

val RoomUser.originalName
    get() = this.event.content.displayName

val RoomUser.avatarUrl
    get() = this.event.content.avatarUrl

val RoomUser.membership
    get() = this.event.content.membership