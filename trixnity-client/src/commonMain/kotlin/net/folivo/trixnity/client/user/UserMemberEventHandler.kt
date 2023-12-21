package net.folivo.trixnity.client.user

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.ClientEvent.StateBaseEvent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.roomIdOrNull
import net.folivo.trixnity.core.model.events.stateKeyOrNull
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.subscribeEventList
import net.folivo.trixnity.core.unsubscribeOnCompletion

private val log = KotlinLogging.logger {}

class UserMemberEventHandler(
    private val api: MatrixClientServerApiClient,
    private val accountStore: AccountStore,
    private val roomUserStore: RoomUserStore,
    private val tm: TransactionManager,
) : EventHandler, LazyMemberEventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeEventList(Priority.STORE_EVENTS, ::setRoomUser).unsubscribeOnCompletion(scope)
        api.sync.subscribe(Priority.AFTER_DEFAULT, ::reloadProfile).unsubscribeOnCompletion(scope)
    }

    private val reloadOwnProfile = MutableStateFlow(false)

    override suspend fun handleLazyMemberEvents(memberEvents: List<StateEvent<MemberEventContent>>) {
        setRoomUser(memberEvents, skipWhenAlreadyPresent = true)
    }

    internal suspend fun setRoomUser(
        events: List<StateBaseEvent<MemberEventContent>>,
        skipWhenAlreadyPresent: Boolean = false
    ) {
        if (events.isNotEmpty()) {
            tm.transaction {
                events.groupBy { it.roomIdOrNull }.forEach { (roomId, eventsByRoomId) ->
                    if (roomId != null) coroutineScope {
                        val allDisplayNames = mutableMapOf<UserId, String>()
                        val putAllDisplayNames = async(start = CoroutineStart.LAZY) {
                            allDisplayNames.putAll(allRoomDisplayNames(roomId))
                        }
                        eventsByRoomId.forEach { event ->
                            val stateKey = event.stateKeyOrNull
                            if (stateKey != null) {
                                val userId = UserId(stateKey)
                                val membership = event.content.membership
                                val newDisplayName = event.content.displayName

                                val hasLeftRoom =
                                    membership == Membership.LEAVE || membership == Membership.BAN

                                val oldDisplayName = roomUserStore.get(userId, roomId).first()?.originalName
                                val hasCollisions =
                                    if (hasLeftRoom || oldDisplayName != newDisplayName) {
                                        if (!oldDisplayName.isNullOrEmpty()) {
                                            putAllDisplayNames.await()
                                            resolveUserDisplayNameCollisions(
                                                oldDisplayName,
                                                allDisplayNames,
                                                true,
                                                userId,
                                                roomId
                                            )
                                        }
                                        if (!newDisplayName.isNullOrEmpty()) {
                                            putAllDisplayNames.await()
                                            resolveUserDisplayNameCollisions(
                                                newDisplayName,
                                                allDisplayNames,
                                                hasLeftRoom,
                                                userId,
                                                roomId
                                            )
                                        } else false
                                    } else false
                                val calculatedName =
                                    calculateUserDisplayName(
                                        newDisplayName,
                                        !hasLeftRoom && !hasCollisions,
                                        userId
                                    )
                                allDisplayNames[userId] = calculatedName
                                log.debug { "calculated displayName in $roomId for $userId is '$calculatedName' (hasCollisions=$hasCollisions, hasLeftRoom=$hasLeftRoom)" }
                                shouldReloadOwnProfile(userId)

                                roomUserStore.update(userId, roomId) { oldRoomUser ->
                                    if (skipWhenAlreadyPresent && oldRoomUser != null) oldRoomUser
                                    else oldRoomUser?.copy(
                                        name = calculatedName,
                                        event = event
                                    ) ?: RoomUser(
                                        roomId = roomId,
                                        userId = userId,
                                        name = calculatedName,
                                        event = event
                                    )
                                }
                            }
                        }
                        putAllDisplayNames.cancel()
                    }
                }
            }
        }
    }

    private suspend fun resolveUserDisplayNameCollisions(
        displayName: String,
        allDisplayNames: Map<UserId, String>,
        isOld: Boolean,
        sourceUserId: UserId,
        roomId: RoomId
    ): Boolean {
        val usersWithSameDisplayName =
            allDisplayNames.filter { it.value == displayName && it.key != sourceUserId }.keys
        if (usersWithSameDisplayName.size == 1) {
            val userId = usersWithSameDisplayName.first()
            val calculatedName = calculateUserDisplayName(displayName, isOld, userId)
            roomUserStore.update(userId, roomId) {
                it?.copy(name = calculatedName)
            }
            log.debug { "found displayName collision '$displayName' of $userId with $sourceUserId in $roomId - new displayName: '$calculatedName'" }
        }
        return usersWithSameDisplayName.isNotEmpty()
    }

    private suspend fun shouldReloadOwnProfile(userId: UserId) {
        if (userId == accountStore.getAccount()?.userId) {
            // only reload profile once, even if there are multiple events in multiple rooms
            reloadOwnProfile.value = true
        }
    }

    private suspend fun reloadProfile() {
        if (reloadOwnProfile.value) {
            reloadOwnProfile.value = false

            accountStore.getAccount()?.userId?.let { userId ->
                api.user.getProfile(userId)
                    .onSuccess {
                        accountStore.updateAccount { account ->
                            account.copy(
                                displayName = it.displayName,
                                avatarUrl = it.avatarUrl
                            )
                        }
                    }.getOrThrow()
            }
        }
    }

    private fun calculateUserDisplayName(
        displayName: String?,
        isUnique: Boolean,
        userId: UserId,
    ): String {
        return when {
            displayName.isNullOrEmpty() -> userId.full
            isUnique -> displayName
            else -> "$displayName (${userId.full})"
        }
    }

    private suspend fun allRoomDisplayNames(
        roomId: RoomId
    ): Map<UserId, String> {
        val memberships = setOf(Membership.JOIN, Membership.INVITE)
        return roomUserStore.getAll(roomId)
            .first()
            .values.asFlow()
            .mapNotNull { it.first() }
            .filter { memberships.contains(it.membership) }
            .mapNotNull { user -> user.originalName?.let { user.userId to it } }
            .toList()
            .toMap()
    }
}