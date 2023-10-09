package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.*
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.room.AvatarEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.roomIdOrNull
import net.folivo.trixnity.core.model.events.senderOrNull
import net.folivo.trixnity.core.model.events.stateKeyOrNull

private val log = KotlinLogging.logger {}

class DirectRoomEventHandler(
    private val userInfo: UserInfo,
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val globalAccountDataStore: GlobalAccountDataStore,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeContent(subscriber = ::setDirectRooms).unsubscribeOnCompletion(scope)
        api.sync.subscribeContent(subscriber = ::setDirectEventContent).unsubscribeOnCompletion(scope)
        api.sync.subscribe(Priority.AFTER_DEFAULT, ::handleDirectEventContent).unsubscribeOnCompletion(scope)
        api.sync.subscribe(Priority.AFTER_DEFAULT, ::setDirectRoomsAfterSync).unsubscribeOnCompletion(scope)
    }

    private val setDirectRoomsEventContent = MutableStateFlow<DirectEventContent?>(null)

    internal suspend fun setDirectRooms(event: ClientEvent<MemberEventContent>) {
        val roomId = event.roomIdOrNull
        val stateKey = event.stateKeyOrNull
        val sender = event.senderOrNull
        if (roomId != null && stateKey != null && sender != null) {
            log.trace { "set direct room $roomId for $stateKey" }
            val userWithMembershipChange = UserId(stateKey)
            val directUser =
                when {
                    userInfo.userId == sender -> userWithMembershipChange
                    userInfo.userId == userWithMembershipChange -> sender
                    sender == userWithMembershipChange -> sender
                    else -> return
                }

            if (directUser != userInfo.userId && event.content.isDirect == true) {
                log.debug { "mark room $roomId as direct room with $directUser" }
                val currentDirectRooms = setDirectRoomsEventContent.value
                    ?: globalAccountDataStore.get<DirectEventContent>().first()?.content
                val existingDirectRoomsWithUser = currentDirectRooms?.mappings?.get(directUser) ?: setOf()
                val newDirectRooms =
                    currentDirectRooms?.copy(currentDirectRooms.mappings + (directUser to (existingDirectRoomsWithUser + roomId)))
                        ?: DirectEventContent(mapOf(directUser to setOf(roomId)))
                setDirectRoomsEventContent.value = newDirectRooms
            }
            if (event.content.membership == Membership.LEAVE || event.content.membership == Membership.BAN) {
                if (directUser != userInfo.userId) {
                    log.debug { "unmark room $roomId as direct room with $directUser" }
                    val currentDirectRooms = setDirectRoomsEventContent.value
                        ?: globalAccountDataStore.get<DirectEventContent>().first()?.content
                    if (currentDirectRooms != null) {
                        val newDirectRooms = DirectEventContent(
                            (currentDirectRooms.mappings + (directUser to (currentDirectRooms.mappings[directUser].orEmpty() - roomId)))
                                .filterValues { it.isNullOrEmpty().not() }
                        )
                        setDirectRoomsEventContent.value = newDirectRooms
                    }
                } else {
                    log.debug { "remove room $roomId from direct rooms, because we left it" }
                    val currentDirectRooms = setDirectRoomsEventContent.value
                        ?: globalAccountDataStore.get<DirectEventContent>().first()?.content
                    if (currentDirectRooms != null) {
                        val newDirectRooms = DirectEventContent(
                            currentDirectRooms.mappings.mapValues { it.value?.minus(roomId) }
                                .filterValues { it.isNullOrEmpty().not() }
                        )
                        setDirectRoomsEventContent.value = newDirectRooms
                    }
                }
            }
        }
    }

    internal suspend fun setDirectRoomsAfterSync() {
        val newDirectRooms = setDirectRoomsEventContent.value
        if (newDirectRooms != null && newDirectRooms != globalAccountDataStore.get<DirectEventContent>()
                .first()?.content
        )
            api.users.setAccountData(newDirectRooms, userInfo.userId)
        setDirectRoomsEventContent.value = null
    }

    // because DirectEventContent could be set before any rooms are in store
    private val directEventContent = MutableStateFlow<DirectEventContent?>(null)

    internal fun setDirectEventContent(directEvent: ClientEvent<DirectEventContent>) {
        directEventContent.value = directEvent.content
    }

    internal suspend fun handleDirectEventContent() {
        val content = directEventContent.value
        if (content != null) {
            setRoomIsDirect(content)
            setAvatarUrlForDirectRooms(content)
            directEventContent.value = null
        }
    }

    internal suspend fun setRoomIsDirect(directEventContent: DirectEventContent) {
        val allDirectRooms = directEventContent.mappings.entries.flatMap { (_, rooms) ->
            rooms ?: emptySet()
        }.toSet()
        allDirectRooms.forEach { room -> roomStore.update(room) { oldRoom -> oldRoom?.copy(isDirect = true) } }

        val allRooms = roomStore.getAll().first().keys
        allRooms.subtract(allDirectRooms)
            .forEach { room -> roomStore.update(room) { oldRoom -> oldRoom?.copy(isDirect = false) } }
    }

    internal suspend fun setAvatarUrlForDirectRooms(directEventContent: DirectEventContent) {
        directEventContent.mappings.entries.forEach { (userId, rooms) ->
            rooms?.forEach { room ->
                if (roomStateStore.getByStateKey<AvatarEventContent>(room).first()?.content?.url.isNullOrEmpty()) {
                    val avatarUrl =
                        roomStateStore.getByStateKey<MemberEventContent>(room, stateKey = userId.full).first()
                            ?.content?.avatarUrl
                    roomStore.update(room) { oldRoom -> oldRoom?.copy(avatarUrl = avatarUrl?.ifEmpty { null }) }
                }
            }
        }
    }
}