package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.*
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.Membership.JOIN
import net.folivo.trixnity.crypto.olm.StoredInboundMegolmSession

inline fun <reified C : StateEventContent> RoomStateStore.get(
    roomId: RoomId,
): Flow<Map<String, Flow<StateBaseEvent<C>?>>> = get(roomId, C::class)

inline fun <reified C : StateEventContent> RoomStateStore.getByStateKey(
    roomId: RoomId,
    stateKey: String = "",
): Flow<StateBaseEvent<C>?> = getByStateKey(roomId, C::class, stateKey)

inline fun <reified C : StateEventContent> RoomStateStore.getContentByStateKey(
    roomId: RoomId,
    stateKey: String = "",
): Flow<C?> = getByStateKey(roomId, C::class, stateKey).map { it?.content }

inline fun <reified C : RoomAccountDataEventContent> RoomAccountDataStore.get(
    roomId: RoomId,
    key: String = "",
): Flow<RoomAccountDataEvent<C>?> = get(roomId, C::class, key)

inline fun <reified C : GlobalAccountDataEventContent> GlobalAccountDataStore.get(
    key: String = "",
): Flow<GlobalAccountDataEvent<C>?> = get(C::class, key)

suspend fun RoomStateStore.members(
    roomId: RoomId,
    memberships: Set<Membership>,
): Set<UserId> =
    get<MemberEventContent>(roomId).first()
        .filter { memberships.contains(it.value.first()?.content?.membership) }
        .map { UserId(it.key) }.toSet()

suspend fun RoomStateStore.membersCount(
    roomId: RoomId,
    membership: Membership,
    vararg moreMemberships: Membership
): Long {
    val allMemberships = moreMemberships.toList() + membership
    return get<MemberEventContent>(roomId).first()
        .count { allMemberships.contains(it.value.first()?.content?.membership) }.toLong()
}

suspend fun RoomStore.encryptedJoinedRooms(): List<RoomId> =
    getAll().first().values
        .map { it.first() }
        .filter { it?.encryptionAlgorithm != null && it.membership == JOIN }
        .mapNotNull { it?.roomId }

fun RoomTimelineStore.getNext(
    event: TimelineEvent,
): Flow<TimelineEvent?>? =
    event.nextEventId?.let { get(it, event.roomId) }

suspend fun RoomTimelineStore.getPrevious(event: TimelineEvent): TimelineEvent? =
    event.previousEventId?.let { get(it, event.roomId) }?.first()

suspend fun KeyStore.isTracked(userId: UserId): Boolean =
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