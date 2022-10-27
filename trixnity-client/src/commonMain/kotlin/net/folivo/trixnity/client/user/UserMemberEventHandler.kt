package net.folivo.trixnity.client.user

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import mu.KotlinLogging
import net.folivo.trixnity.client.getRoomId
import net.folivo.trixnity.client.getStateKey
import net.folivo.trixnity.client.store.AccountStore
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.RoomUserStore
import net.folivo.trixnity.client.store.originalName
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribe

private val log = KotlinLogging.logger {}

class UserMemberEventHandler(
    private val api: MatrixClientServerApiClient,
    private val accountStore: AccountStore,
    private val roomUserStore: RoomUserStore,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(::setRoomUser)
        api.sync.subscribeAfterSyncResponse(::reloadProfile)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.unsubscribe(::setRoomUser)
            api.sync.unsubscribeAfterSyncResponse(::reloadProfile)
        }
    }

    private val reloadOwnProfile = MutableStateFlow(false)

    internal suspend fun setRoomUser(event: Event<MemberEventContent>, skipWhenAlreadyPresent: Boolean = false) {
        val roomId = event.getRoomId()
        val stateKey = event.getStateKey()
        if (roomId != null && stateKey != null) {
            val userId = UserId(stateKey)
            val storedRoomUser = roomUserStore.get(userId, roomId).first()
            if (skipWhenAlreadyPresent && storedRoomUser != null) return
            val membership = event.content.membership
            val newDisplayName = event.content.displayName

            val hasLeftRoom =
                membership == Membership.LEAVE || membership == Membership.BAN

            val oldDisplayName = roomUserStore.get(userId, roomId).first()?.originalName
            val hasCollisions = if (hasLeftRoom || oldDisplayName != newDisplayName) {
                if (!oldDisplayName.isNullOrEmpty())
                    resolveUserDisplayNameCollisions(oldDisplayName, true, userId, roomId)
                if (!newDisplayName.isNullOrEmpty())
                    resolveUserDisplayNameCollisions(newDisplayName, hasLeftRoom, userId, roomId)
                else false
            } else false
            val calculatedName = calculateUserDisplayName(newDisplayName, !hasLeftRoom && !hasCollisions, userId)
            log.debug { "calculated displayName in $roomId for $userId is '$calculatedName' (hasCollisions=$hasCollisions, hasLeftRoom=$hasLeftRoom)" }

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

            shouldReloadOwnProfile(userId)
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

    private fun shouldReloadOwnProfile(userId: UserId) {
        if (userId == accountStore.userId.value) {
            // only reload profile once, even if there are multiple events in multiple rooms
            reloadOwnProfile.value = true
        }
    }

    private suspend fun reloadProfile(syncResponse: Sync.Response) {
        if (reloadOwnProfile.value) {
            reloadOwnProfile.value = false

            accountStore.userId.value?.let { userId ->
                api.users.getProfile(userId)
                    .onSuccess {
                        accountStore.displayName.value = it.displayName
                        accountStore.avatarUrl.value = it.avatarUrl
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