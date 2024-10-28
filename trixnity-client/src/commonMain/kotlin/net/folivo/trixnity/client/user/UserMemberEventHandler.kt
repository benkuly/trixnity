package net.folivo.trixnity.client.user

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.ClientEvent.StateBaseEvent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.roomIdOrNull
import net.folivo.trixnity.core.subscribeEventList
import net.folivo.trixnity.core.unsubscribeOnCompletion

private val log = KotlinLogging.logger {}

class UserMemberEventHandler(
    private val api: MatrixClientServerApiClient,
    private val accountStore: AccountStore,
    private val roomUserStore: RoomUserStore,
    private val userInfo: UserInfo,
    private val tm: TransactionManager,
) : EventHandler, LazyMemberEventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeEventList(Priority.STORE_EVENTS, ::setRoomUser).unsubscribeOnCompletion(scope)
        api.sync.subscribeEventList(Priority.DEFAULT, ::reloadOwnProfile).unsubscribeOnCompletion(scope)
    }

    override suspend fun handleLazyMemberEvents(memberEvents: List<StateEvent<MemberEventContent>>) {
        setRoomUser(memberEvents, skipWhenAlreadyPresent = true)
    }

    private val membershipsToConsiderInCollisionDetection = setOf(Membership.JOIN, Membership.INVITE, Membership.KNOCK)

    internal suspend fun setRoomUser(
        events: List<StateBaseEvent<MemberEventContent>>,
        skipWhenAlreadyPresent: Boolean = false
    ) {
        if (events.isNotEmpty()) {
            log.debug { "updated room members" }
            coroutineScope {
                val roomUserUpdates = events.groupBy { it.roomIdOrNull }
                    .mapValues { (_, value) -> value.associateBy { UserId(it.stateKey) } }
                    .map { (roomId, newMemberEvents) ->
                        async {
                            if (roomId == null) return@async listOf()

                            val displayNameOrMembershipChange = newMemberEvents
                                .any { (userId, newEvent) ->
                                    val currentRoomUser = roomUserStore.get(userId, roomId).first()

                                    val currentDisplayName = currentRoomUser?.originalName
                                    val newDisplayName = newEvent.content.displayName

                                    val displayNameChange =
                                        currentRoomUser == null ||
                                                currentDisplayName != newDisplayName

                                    val membershipChange =
                                        currentRoomUser == null ||
                                                !membershipsToConsiderInCollisionDetection.contains(currentRoomUser.membership) ||
                                                !membershipsToConsiderInCollisionDetection.contains(newEvent.content.membership)

                                    displayNameChange || membershipChange
                                }

                            if (displayNameOrMembershipChange) {
                                val currentRoomUsers = roomUserStore.getAll(roomId).first()
                                    .values.asFlow()
                                    .mapNotNull { it.first() }
                                    .filter { membershipsToConsiderInCollisionDetection.contains(it.membership) }
                                    .map { it.userId to it }
                                    .toList()
                                    .toMap()

                                val currentDisplayNames = currentRoomUsers.mapValues { it.value.originalName }
                                val newDisplayNames = newMemberEvents
                                    .filter { membershipsToConsiderInCollisionDetection.contains(it.value.content.membership) }
                                    .mapValues { it.value.content.displayName }

                                val currentDisplayNameCollisions = currentDisplayNames.findCollisions()
                                val newDisplayNameCollisions = (currentDisplayNames + newDisplayNames).findCollisions()
                                log.trace { "currentDisplayNameCollisions=$currentDisplayNameCollisions" }
                                log.trace { "newDisplayNameCollisions=$newDisplayNameCollisions" }

                                val resolveFormerCollisions =
                                    (currentDisplayNameCollisions - newDisplayNameCollisions.keys)
                                        .resolveCollision(roomId, newMemberEvents, currentRoomUsers, isUnique = true)

                                val resolveNewCollisions =
                                    (newDisplayNameCollisions - currentDisplayNameCollisions.keys)
                                        .resolveCollision(roomId, newMemberEvents, currentRoomUsers, isUnique = false)

                                val newRoomUsers = newMemberEvents.map { (userId, newEvent) ->
                                    val displayName = newEvent.content.displayName
                                    val isUnique = !newDisplayNameCollisions.contains(displayName)
                                    (userId to roomId) to RoomUser(
                                        roomId = roomId,
                                        userId = userId,
                                        name = calculateName(userId, displayName, isUnique),
                                        event = newEvent
                                    )
                                }
                                resolveFormerCollisions + resolveNewCollisions + newRoomUsers
                            } else {
                                log.trace { "no collisions found" }
                                newMemberEvents.map { (userId, newEvent) ->
                                    (userId to roomId) to (
                                            roomUserStore.get(userId, roomId).first()?.copy(
                                                event = newEvent // we are sure, that displayName has not been changed
                                            ) ?: RoomUser(
                                                roomId = roomId,
                                                userId = userId,
                                                name = calculateName(userId, newEvent.content.displayName, true),
                                                event = newEvent
                                            ))
                                }
                            }
                        }
                    }.awaitAll().flatten().toMap()
                tm.transaction {
                    roomUserUpdates.forEach { (key, roomUser) ->
                        roomUserStore.update(key.first, key.second) { oldRoomUser ->
                            if (skipWhenAlreadyPresent && oldRoomUser != null) oldRoomUser
                            else roomUser
                        }
                    }
                }
            }
        }
    }

    private fun Map<UserId, String?>.findCollisions(): Map<String?, List<UserId>> =
        entries
            .groupBy { it.value }
            .filterKeys { !it.isNullOrEmpty() }
            .filterValues { it.size > 1 }
            .mapValues { (_, value) -> value.map { it.key } }

    private fun Map<String?, List<UserId>>.resolveCollision(
        roomId: RoomId,
        newMemberEvents: Map<UserId, StateBaseEvent<MemberEventContent>>,
        currentRoomUsers: Map<UserId, RoomUser>,
        isUnique: Boolean
    ): List<Pair<Pair<UserId, RoomId>, RoomUser>> =
        asSequence()
            .flatMap { it.value }
            .minus(newMemberEvents.keys)
            .distinct()
            .mapNotNull { currentRoomUsers[it] }
            .map { roomUser ->
                val userId = roomUser.userId
                (userId to roomId) to roomUser.copy(
                    name = calculateName(userId, roomUser.originalName, isUnique)
                )
            }
            .toList()

    private fun calculateName(
        userId: UserId,
        displayName: String?,
        isUnique: Boolean,
    ): String =
        when {
            displayName.isNullOrEmpty() -> userId.full
            isUnique -> displayName
            else -> "$displayName (${userId.full})"
        }

    internal suspend fun reloadOwnProfile(events: List<StateBaseEvent<MemberEventContent>>) {
        // TODO could be optimized by checking if displayname or avatarUrl has been changed
        if (events.any { it.stateKey == userInfo.userId.full }) {
            log.debug { "reload own profile as there has been member events of us" }
            api.user.getProfile(userInfo.userId)
                .onSuccess {
                    accountStore.updateAccount { account ->
                        account?.copy(
                            displayName = it.displayName,
                            avatarUrl = it.avatarUrl
                        )
                    }
                }.getOrThrow()
        }
    }
}