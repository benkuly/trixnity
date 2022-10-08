package net.folivo.trixnity.client.store

import kotlinx.coroutines.flow.Flow
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

inline fun <reified C : StateEventContent> RoomStateStore.get(
    roomId: RoomId,
): Flow<Map<String, Event<C>?>?> = get(roomId, C::class)

inline fun <reified C : StateEventContent> RoomStateStore.getByStateKey(
    roomId: RoomId,
    stateKey: String = "",
): Flow<Event<C>?> = getByStateKey(roomId, stateKey, C::class)

inline fun <reified C : RoomAccountDataEventContent> RoomAccountDataStore.get(
    roomId: RoomId,
    key: String = "",
): Flow<RoomAccountDataEvent<C>?> = get(roomId, C::class, key)

inline fun <reified C : GlobalAccountDataEventContent> GlobalAccountDataStore.get(
    key: String = "",
): Flow<GlobalAccountDataEvent<C>?> = get(C::class, key)

suspend inline fun RoomStateStore.members(
    roomId: RoomId,
    membership: Membership,
    vararg moreMemberships: Membership
): Set<UserId> {
    val allMemberships = moreMemberships.toList() + membership
    return get<MemberEventContent>(roomId).first()
        ?.filter { allMemberships.contains(it.value?.content?.membership) }
        ?.map { UserId(it.key) }?.toSet() ?: setOf()
}

suspend inline fun RoomStateStore.membersCount(
    roomId: RoomId,
    membership: Membership,
    vararg moreMemberships: Membership
): Long {
    val allMemberships = moreMemberships.toList() + membership
    return get<MemberEventContent>(roomId).first()
        ?.count { allMemberships.contains(it.value?.content?.membership) }?.toLong() ?: 0
}

fun RoomStore.encryptedJoinedRooms(): List<RoomId> =
    getAll().value.values
        .filter { it.value?.encryptionAlgorithm != null && it.value?.membership == JOIN }
        .mapNotNull { it.value?.roomId }

inline fun RoomTimelineStore.getNext(
    event: TimelineEvent,
): Flow<TimelineEvent?>? =
    event.nextEventId?.let { get(it, event.roomId) }

suspend inline fun RoomTimelineStore.getPrevious(event: TimelineEvent): TimelineEvent? =
    event.previousEventId?.let { get(it, event.roomId) }?.first()

suspend inline fun KeyStore.isTracked(userId: UserId): Boolean =
    getDeviceKeys(userId).first() != null

suspend inline fun OlmCryptoStore.waitForInboundMegolmSession(
    roomId: RoomId,
    sessionId: String,
    firstKnownIndexLessThen: Long? = null
) {
    getInboundMegolmSession(sessionId, roomId)
        .first { it != null && (firstKnownIndexLessThen == null || it.firstKnownIndex < firstKnownIndexLessThen) }
}

val RoomUser.originalName
    get() = this.event.content.displayName

val RoomUser.avatarUrl
    get() = this.event.content.avatarUrl

val RoomUser.membership
    get() = this.event.content.membership