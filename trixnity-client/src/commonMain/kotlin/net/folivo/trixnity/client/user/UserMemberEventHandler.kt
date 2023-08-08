package net.folivo.trixnity.client.user

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import net.folivo.trixnity.client.getRoomId
import net.folivo.trixnity.client.getStateKey
import net.folivo.trixnity.client.store.AccountStore
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.RoomUserStore
import net.folivo.trixnity.client.store.originalName
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.utils.filter
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.sync.Sync
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
        api.sync.syncResponse.subscribe(::setAllRoomUsers, 90)
        api.sync.afterSyncResponse.subscribe(::reloadProfile)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.syncResponse.unsubscribe(::setAllRoomUsers)
            api.sync.afterSyncResponse.unsubscribe(::reloadProfile)
        }
    }

    private val reloadOwnProfile = MutableStateFlow(false)

    internal suspend fun setAllRoomUsers(syncResponse: Sync.Response) {
        setRoomUser(
            syncResponse.filter<MemberEventContent>().filterIsInstance<Event<MemberEventContent>>().toList()
        )
    }

    override suspend fun handleLazyMemberEvents(memberEvents: List<Event<MemberEventContent>>) {
        setRoomUser(memberEvents, skipWhenAlreadyPresent = true)
    }

    internal suspend fun setRoomUser(events: List<Event<MemberEventContent>>, skipWhenAlreadyPresent: Boolean = false) {
        if (events.isNotEmpty()) {
            val updatedRoomUsers =
                events.mapNotNull { event ->
                    val roomId = event.getRoomId()
                    val stateKey = event.getStateKey()
                    if (roomId != null && stateKey != null) {
                        val userId = UserId(stateKey)
                        val membership = event.content.membership
                        val newDisplayName = event.content.displayName

                        val hasLeftRoom =
                            membership == Membership.LEAVE || membership == Membership.BAN

                        val oldDisplayName = roomUserStore.get(userId, roomId).first()?.originalName
                        val hasCollisions =
                            if (hasLeftRoom || oldDisplayName != newDisplayName) {
                                if (!oldDisplayName.isNullOrEmpty())
                                    resolveUserDisplayNameCollisions(oldDisplayName, true, userId, roomId)
                                if (!newDisplayName.isNullOrEmpty())
                                    resolveUserDisplayNameCollisions(newDisplayName, hasLeftRoom, userId, roomId)
                                else false
                            } else false
                        val calculatedName =
                            calculateUserDisplayName(newDisplayName, !hasLeftRoom && !hasCollisions, userId)
                        log.debug { "calculated displayName in $roomId for $userId is '$calculatedName' (hasCollisions=$hasCollisions, hasLeftRoom=$hasLeftRoom)" }
                        shouldReloadOwnProfile(userId)
                        RoomUser(
                            roomId = roomId,
                            userId = userId,
                            name = calculatedName,
                            event = event
                        )
                    } else null
                }
            tm.writeTransaction {
                updatedRoomUsers.forEach { updatedRoomUser ->
                    roomUserStore.update(updatedRoomUser.userId, updatedRoomUser.roomId) { oldRoomUser ->
                        if (skipWhenAlreadyPresent && oldRoomUser != null) oldRoomUser
                        else oldRoomUser?.copy(
                            name = updatedRoomUser.name,
                            event = updatedRoomUser.event,
                        ) ?: updatedRoomUser
                    }
                }
            }
        }
    }

    private suspend fun resolveUserDisplayNameCollisions(
        displayName: String,
        isOld: Boolean,
        sourceUserId: UserId,
        roomId: RoomId
    ): Boolean {
        val usersWithSameDisplayName =
            roomUserStore.getByOriginalNameAndMembership(
                displayName,
                setOf(Membership.JOIN, Membership.INVITE),
                roomId
            ) - sourceUserId
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

    private suspend fun reloadProfile(syncResponse: Sync.Response) {
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
}