package net.folivo.trixnity.client.user

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mu.KotlinLogging
import net.folivo.trixnity.client.getRoomId
import net.folivo.trixnity.client.getSender
import net.folivo.trixnity.client.getStateKey
import net.folivo.trixnity.client.retryWhenSyncIs
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.Store
import net.folivo.trixnity.client.store.isTracked
import net.folivo.trixnity.client.store.originalName
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncState
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.*
import net.folivo.trixnity.core.subscribe
import kotlin.reflect.KClass

private val log = KotlinLogging.logger {}

interface IUserService {
    val userPresence: StateFlow<Map<UserId, PresenceEventContent>>
    fun loadMembers(roomId: RoomId)

    suspend fun getAll(roomId: RoomId, scope: CoroutineScope): StateFlow<Set<RoomUser>?>

    suspend fun getAll(roomId: RoomId): Set<RoomUser>?

    suspend fun getById(userId: UserId, roomId: RoomId, scope: CoroutineScope): StateFlow<RoomUser?>

    suspend fun getById(userId: UserId, roomId: RoomId): RoomUser?

    suspend fun <C : GlobalAccountDataEventContent> getAccountData(
        eventContentClass: KClass<C>,
        key: String = "",
        scope: CoroutineScope
    ): StateFlow<C?>

    suspend fun <C : GlobalAccountDataEventContent> getAccountData(
        eventContentClass: KClass<C>,
        key: String = "",
    ): C?
}

class UserService(
    private val store: Store,
    private val api: MatrixClientServerApiClient,
    private val currentSyncState: StateFlow<SyncState>,
    private val scope: CoroutineScope,
) : IUserService {
    private val reloadOwnProfile = MutableStateFlow(false)
    private val currentlyLoadingMembers = MutableStateFlow<Set<RoomId>>(setOf())
    private val _userPresence = MutableStateFlow(mapOf<UserId, PresenceEventContent>())
    override val userPresence = _userPresence.asStateFlow()

    init {
        api.sync.subscribe(::setGlobalAccountData)
        api.sync.subscribe(::setRoomUser)
        api.sync.subscribe(::setPresence)
        api.sync.subscribeAfterSyncResponse { reloadProfile() }
    }

    internal fun setPresence(presenceEvent: Event<PresenceEventContent>) {
        presenceEvent.getSender()?.let { sender ->
            _userPresence.update { oldValue -> oldValue + (sender to presenceEvent.content) }
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

    private suspend fun resolveUserDisplayNameCollisions(
        displayName: String,
        isOld: Boolean,
        sourceUserId: UserId,
        roomId: RoomId
    ): Boolean {
        val usersWithSameDisplayName =
            store.roomUser.getByOriginalNameAndMembership(displayName, setOf(JOIN, INVITE), roomId) - sourceUserId
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
                membership == LEAVE || membership == BAN

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
                        store.account.avatarUrl.value = it.avatarUrl
                    }.getOrThrow()
            }
        }
    }

    override fun loadMembers(roomId: RoomId) {
        if (currentlyLoadingMembers.getAndUpdate { it + roomId }.contains(roomId).not()) {
            scope.launch {
                currentSyncState.retryWhenSyncIs(
                    SyncState.RUNNING,
                    onError = { log.warn(it) { "failed loading members" } },
                ) {
                    if (store.room.get(roomId).value?.membersLoaded != true) {
                        val memberEvents = api.rooms.getMembers(
                            roomId = roomId,
                            notMembership = LEAVE
                        ).getOrThrow().toList()
                        memberEvents.forEach {
                            store.roomState.update(event = it, skipWhenAlreadyPresent = true)
                            setRoomUser(event = it, skipWhenAlreadyPresent = true)
                        }
                        if (store.room.get(roomId).value?.encryptionAlgorithm != null) {
                            store.keys.outdatedKeys.update {
                                it + memberEvents.map { event -> UserId(event.stateKey) }
                                    .filterNot { userId -> store.keys.isTracked(userId) }
                            }
                        }
                        store.room.update(roomId) { it?.copy(membersLoaded = true) }
                    }
                }
                currentlyLoadingMembers.update { it - roomId }
            }
        }
    }

    internal suspend fun setGlobalAccountData(accountDataEvent: Event<GlobalAccountDataEventContent>) {
        if (accountDataEvent is Event.GlobalAccountDataEvent) {
            store.globalAccountData.update(accountDataEvent)
        }
    }

    override suspend fun getAll(roomId: RoomId, scope: CoroutineScope): StateFlow<Set<RoomUser>?> {
        return store.roomUser.getAll(roomId, scope)
    }

    override suspend fun getAll(roomId: RoomId): Set<RoomUser>? {
        return store.roomUser.getAll(roomId)
    }

    override suspend fun getById(userId: UserId, roomId: RoomId, scope: CoroutineScope): StateFlow<RoomUser?> {
        return store.roomUser.get(userId, roomId, scope)
    }

    override suspend fun getById(userId: UserId, roomId: RoomId): RoomUser? {
        return store.roomUser.get(userId, roomId)
    }

    override suspend fun <C : GlobalAccountDataEventContent> getAccountData(
        eventContentClass: KClass<C>,
        key: String,
        scope: CoroutineScope
    ): StateFlow<C?> {
        return store.globalAccountData.get(eventContentClass, key, scope)
            .map { it?.content }
            .stateIn(scope)
    }

    override suspend fun <C : GlobalAccountDataEventContent> getAccountData(
        eventContentClass: KClass<C>,
        key: String,
    ): C? {
        return store.globalAccountData.get(eventContentClass, key)?.content
    }
}