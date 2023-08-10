package net.folivo.trixnity.client.user

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.getRoomId
import net.folivo.trixnity.client.getStateKey
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.utils.filterContent
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncProcessingData
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership

private val log = KotlinLogging.logger {}

class UserMemberEventHandler(
    private val api: MatrixClientServerApiClient,
    private val accountStore: AccountStore,
    private val roomUserStore: RoomUserStore,
    private val tm: RepositoryTransactionManager,
) : EventHandler, LazyMemberEventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.syncProcessing.subscribe(::setAllRoomUsers, 90)
        api.sync.afterSyncProcessing.subscribe(::reloadProfile)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.syncProcessing.unsubscribe(::setAllRoomUsers)
            api.sync.afterSyncProcessing.unsubscribe(::reloadProfile)
        }
    }

    private val reloadOwnProfile = MutableStateFlow(false)

    internal suspend fun setAllRoomUsers(syncProcessingData: SyncProcessingData) {
        setRoomUser(
            syncProcessingData.allEvents.filterContent<MemberEventContent>().toList()
        )
    }

    override suspend fun handleLazyMemberEvents(memberEvents: List<Event<MemberEventContent>>) {
        setRoomUser(memberEvents, skipWhenAlreadyPresent = true)
    }

    internal suspend fun setRoomUser(events: List<Event<MemberEventContent>>, skipWhenAlreadyPresent: Boolean = false) {
        if (events.isNotEmpty()) {
            tm.writeTransaction {
                events.groupBy { it.getRoomId() }.forEach { (roomId, eventsByRoomId) ->
                    if (roomId != null) coroutineScope {
                        val allDisplayNames = mutableMapOf<UserId, String>()
                        val putAllDisplayNames = async(start = CoroutineStart.LAZY) {// FIXME test
                            allDisplayNames.putAll(allRoomDisplayNames(roomId))
                        }
                        eventsByRoomId.forEach { event ->
                            val stateKey = event.getStateKey()
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

    private suspend fun reloadProfile(syncProcessingData: SyncProcessingData) {
        if (reloadOwnProfile.value) {
            reloadOwnProfile.value = false

            accountStore.getAccount()?.userId?.let { userId ->
                api.users.getProfile(userId)
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
            ?.values?.asFlow()
            ?.mapNotNull { it.first() }
            ?.filter { memberships.contains(it.membership) }
            ?.mapNotNull { user -> user.originalName?.let { user.userId to it } }
            ?.toList()
            ?.toMap()
            .orEmpty()
    }
}