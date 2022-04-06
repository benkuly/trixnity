package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.Event.RoomAccountDataEvent
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.core.model.keys.Key

suspend inline fun <reified C : StateEventContent> RoomStateStore.get(
    roomId: RoomId,
    scope: CoroutineScope
): StateFlow<Map<String, Event<C>?>?> = get(roomId, C::class, scope)

suspend inline fun <reified C : StateEventContent> RoomStateStore.get(
    roomId: RoomId,
): Map<String, Event<C>?>? = get(roomId, C::class)

suspend inline fun <reified C : StateEventContent> RoomStateStore.getByStateKey(
    roomId: RoomId,
    stateKey: String = "",
    scope: CoroutineScope
): StateFlow<Event<C>?> = getByStateKey(roomId, stateKey, C::class, scope)

suspend inline fun <reified C : StateEventContent> RoomStateStore.getByStateKey(
    roomId: RoomId,
    stateKey: String = ""
): Event<C>? = getByStateKey(roomId, stateKey, C::class)

suspend inline fun <reified C : RoomAccountDataEventContent> RoomAccountDataStore.get(
    roomId: RoomId,
    key: String = "",
    scope: CoroutineScope
): StateFlow<RoomAccountDataEvent<C>?> = get(roomId, C::class, key, scope)

suspend inline fun <reified C : GlobalAccountDataEventContent> GlobalAccountDataStore.get(
    key: String = "",
    scope: CoroutineScope
): StateFlow<GlobalAccountDataEvent<C>?> = get(C::class, key, scope)

suspend inline fun <reified C : GlobalAccountDataEventContent> GlobalAccountDataStore.get(
    key: String = ""
): GlobalAccountDataEvent<C>? = get(C::class, key)

suspend inline fun RoomStateStore.members(
    roomId: RoomId,
    membership: Membership,
    vararg moreMemberships: Membership
): Set<UserId> {
    val allMemberships = moreMemberships.toList() + membership
    return get<MemberEventContent>(roomId)
        ?.filter { allMemberships.contains(it.value?.content?.membership) }
        ?.map { UserId(it.key) }?.toSet() ?: setOf()
}

suspend inline fun RoomStateStore.membersCount(
    roomId: RoomId,
    membership: Membership,
    vararg moreMemberships: Membership
): Long {
    val allMemberships = moreMemberships.toList() + membership
    return get<MemberEventContent>(roomId)
        ?.count { allMemberships.contains(it.value?.content?.membership) }?.toLong() ?: 0
}

fun RoomStore.encryptedJoinedRooms(): List<RoomId> =
    getAll().value.values
        .filter { it.value?.encryptionAlgorithm != null && it.value?.membership == JOIN }
        .mapNotNull { it.value?.roomId }

suspend inline fun RoomTimelineStore.getNext(
    event: TimelineEvent,
    scope: CoroutineScope
): StateFlow<TimelineEvent?>? =
    event.nextEventId?.let { get(it, event.roomId, scope) }

suspend inline fun RoomTimelineStore.getNext(
    event: TimelineEvent,
    withTransaction: Boolean = true
): TimelineEvent? =
    event.nextEventId?.let { get(it, event.roomId, withTransaction = withTransaction) }

suspend inline fun RoomTimelineStore.getPrevious(
    event: TimelineEvent,
    withTransaction: Boolean = true
): TimelineEvent? =
    event.previousEventId?.let { get(it, event.roomId, withTransaction = withTransaction) }

suspend inline fun KeyStore.isTracked(userId: UserId): Boolean =
    getDeviceKeys(userId) != null

suspend inline fun OlmStore.waitForInboundMegolmSession(
    roomId: RoomId,
    sessionId: String,
    senderKey: Key.Curve25519Key,
    scope: CoroutineScope,
    firstKnownIndexLessThen: Long? = null
) {
    getInboundMegolmSession(senderKey, sessionId, roomId, scope)
        .first { it != null && (firstKnownIndexLessThen == null || it.firstKnownIndex < firstKnownIndexLessThen) }
}

val RoomUser.originalName
    get() = this.event.content.displayName

val RoomUser.avatarUrl
    get() = this.event.content.avatarUrl

val RoomUser.membership
    get() = this.event.content.membership