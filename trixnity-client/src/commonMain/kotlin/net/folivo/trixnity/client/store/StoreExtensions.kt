package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession

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
    memberships: Set<Membership>,
): Set<UserId> =
    get<MemberEventContent>(roomId).first()
        ?.filter { memberships.contains(it.value?.content?.membership) }
        ?.map { UserId(it.key) }?.toSet() ?: setOf()

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

suspend fun OlmCryptoStore.waitForInboundMegolmSession(
    roomId: RoomId,
    sessionId: String,
    firstKnownIndexLessThen: Long? = null,
    onNotExisting: (suspend CoroutineScope.() -> Unit)? = null
): Unit = coroutineScope {
    fun StoredInboundMegolmSession?.matches() =
        this != null && (firstKnownIndexLessThen == null || this.firstKnownIndex < firstKnownIndexLessThen)

    val onNotExistingJob =
        if (getInboundMegolmSession(sessionId, roomId).first().matches().not() && onNotExisting != null)
            launch { onNotExisting() }
        else null
    getInboundMegolmSession(sessionId, roomId)
        .first { it.matches() }
    onNotExistingJob?.cancel()
}

val RoomUser.originalName
    get() = this.event.content.displayName

val RoomUser.avatarUrl
    get() = this.event.content.avatarUrl

val RoomUser.membership
    get() = this.event.content.membership