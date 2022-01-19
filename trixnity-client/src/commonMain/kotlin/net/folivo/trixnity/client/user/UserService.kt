package net.folivo.trixnity.client.user

import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mu.KotlinLogging
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.SyncApiClient.SyncState.RUNNING
import net.folivo.trixnity.client.api.model.rooms.Membership
import net.folivo.trixnity.client.getRoomId
import net.folivo.trixnity.client.getStateKey
import net.folivo.trixnity.client.retryWhenSyncIs
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.originalName
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import kotlin.reflect.KClass

private val log = KotlinLogging.logger {}

class UserService(
    private val store: Store,
    private val api: MatrixApiClient,
) {
    private val reloadOwnProfile = MutableStateFlow(false)
    private val loadMembersQueue = MutableStateFlow<Set<RoomId>>(setOf())

    suspend fun start(scope: CoroutineScope) {
        api.sync.subscribe(::setGlobalAccountData)
        api.sync.subscribe(::setRoomUser)
        api.sync.subscribeAfterSyncResponse(::reloadProfile)
        // we use UNDISPATCHED because we want to ensure, that collect is called immediately
        scope.launch(start = CoroutineStart.UNDISPATCHED) { handleLoadMembersQueue() }
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

    private suspend fun resolveUserDisplayNameCollisions(
        displayName: String,
        isOld: Boolean,
        sourceUserId: UserId,
        roomId: RoomId
    ): Boolean {
        val usersWithSameDisplayName =
            store.roomUser.getByOriginalNameAndMembership(
                displayName, setOf(
                    MemberEventContent.Membership.JOIN,
                    MemberEventContent.Membership.INVITE
                ), roomId
            ) - sourceUserId
        if (usersWithSameDisplayName.size == 1) {
            val userId = usersWithSameDisplayName.first()
            val calculatedName = calculateUserDisplayName(displayName, isOld, userId)
            store.roomUser.update(userId, roomId) {
                it?.copy(name = calculatedName)
            }
            log.debug { "found displayName collision '$displayName' of $userId with $sourceUserId in $roomId - new displayName: '$calculatedName'" }
        }
        return usersWithSameDisplayName.isNotEmpty()
    }

    internal suspend fun setRoomUser(event: Event<MemberEventContent>, skipWhenAlreadyPresent: Boolean = false) {
        val roomId = event.getRoomId()
        val stateKey = event.getStateKey()
        if (roomId != null && stateKey != null) {
            val userId = UserId(stateKey)
            val storedRoomUser = store.roomUser.get(userId, roomId)
            if (skipWhenAlreadyPresent && storedRoomUser != null) return
            val membership = event.content.membership
            val newDisplayName = event.content.displayName

            val hasLeftRoom =
                membership == MemberEventContent.Membership.LEAVE || membership == MemberEventContent.Membership.BAN

            val oldDisplayName = store.roomUser.get(userId, roomId)?.originalName
            val hasCollisions = if (hasLeftRoom || oldDisplayName != newDisplayName) {
                if (!oldDisplayName.isNullOrEmpty())
                    resolveUserDisplayNameCollisions(oldDisplayName, true, userId, roomId)
                if (!newDisplayName.isNullOrEmpty())
                    resolveUserDisplayNameCollisions(newDisplayName, hasLeftRoom, userId, roomId)
                else false
            } else false
            val calculatedName = calculateUserDisplayName(newDisplayName, !hasLeftRoom && !hasCollisions, userId)
            log.debug { "calculated displayName in $roomId for $userId is '$calculatedName' (hasCollisions=$hasCollisions, hasLeftRoom=$hasLeftRoom)" }

            store.roomUser.update(userId, roomId) { oldRoomUser ->
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

    private fun shouldReloadOwnProfile(userId: UserId) {
        if (userId == store.account.userId.value) {
            // only reload profile once, even if there are multiple events in multiple rooms
            reloadOwnProfile.value = true
        }
    }

    private suspend fun reloadProfile() {
        if (reloadOwnProfile.value) {
            reloadOwnProfile.value = false

            store.account.userId.value?.let { userId ->
                api.users.getProfile(userId)
                    .onSuccess {
                        store.account.displayName.value = it.displayName
                        store.account.avatarUrl.value = it.avatarUrl?.let { url -> Url(url) }
                    }.getOrThrow()
            }
        }
    }

    fun loadMembers(roomId: RoomId) = loadMembersQueue.update { it + roomId }

    internal suspend fun handleLoadMembersQueue() = coroutineScope {
        api.sync.currentSyncState.retryWhenSyncIs(
            RUNNING,
            onError = { log.warn(it) { "failed loading members" } },
            scope = this
        ) {
            loadMembersQueue.collect { roomIds ->
                roomIds.forEach { roomId ->
                    if (store.room.get(roomId).value?.membersLoaded != true) {
                        val memberEvents = api.rooms.getMembers(
                            roomId = roomId,
                            notMembership = Membership.LEAVE
                        ).getOrThrow().toList()
                        memberEvents.forEach {
                            store.roomState.update(event = it, skipWhenAlreadyPresent = true)
                            setRoomUser(event = it, skipWhenAlreadyPresent = true)
                        }
                        store.room.update(roomId) { it?.copy(membersLoaded = true) }
                    }
                    loadMembersQueue.update { it - roomId }
                }
            }
        }
    }

    internal suspend fun setGlobalAccountData(accountDataEvent: Event<GlobalAccountDataEventContent>) {
        if (accountDataEvent is Event.GlobalAccountDataEvent) {
            store.globalAccountData.update(accountDataEvent)
        }
    }

    suspend fun getAll(roomId: RoomId, scope: CoroutineScope): StateFlow<Set<RoomUser>?> {
        return store.roomUser.getAll(roomId, scope)
    }

    suspend fun getAll(roomId: RoomId): Set<RoomUser>? {
        return store.roomUser.getAll(roomId)
    }

    suspend fun getById(userId: UserId, roomId: RoomId, scope: CoroutineScope): StateFlow<RoomUser?> {
        return store.roomUser.get(userId, roomId, scope)
    }

    suspend fun getById(userId: UserId, roomId: RoomId): RoomUser? {
        return store.roomUser.get(userId, roomId)
    }

    suspend fun <C : GlobalAccountDataEventContent> getAccountData(
        eventContentClass: KClass<C>,
        key: String = "",
        scope: CoroutineScope
    ): StateFlow<C?> {
        return store.globalAccountData.get(eventContentClass, key, scope)
            .map { it?.content }
            .stateIn(scope)
    }

    suspend fun <C : GlobalAccountDataEventContent> getAccountData(
        eventContentClass: KClass<C>,
        key: String = "",
    ): C? {
        return store.globalAccountData.get(eventContentClass, key)?.content
    }
}