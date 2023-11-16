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
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.room.AvatarEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.BAN
import net.folivo.trixnity.core.model.events.m.room.Membership.LEAVE

private val log = KotlinLogging.logger {}

class DirectRoomEventHandler(
    private val userInfo: UserInfo,
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val globalAccountDataStore: GlobalAccountDataStore,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeEventList(Priority.AFTER_DEFAULT, ::setNewDirectEventFromMemberEvent)
            .unsubscribeOnCompletion(scope)
        api.sync.subscribeContent(Priority.AFTER_DEFAULT, ::setDirectRoomProperties).unsubscribeOnCompletion(scope)
    }

    internal suspend fun setNewDirectEventFromMemberEvent(events: List<StateEvent<MemberEventContent>>) {
        val initialDirectEventContent = globalAccountDataStore.get<DirectEventContent>().first()?.content
        val directEventContent = MutableStateFlow(initialDirectEventContent)

        events.forEach { event ->
            val currentDirectRooms = directEventContent.value
            val roomId = event.roomId
            val stateKey = event.stateKey
            val sender = event.sender
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
                val existingDirectRoomsWithUser = currentDirectRooms?.mappings?.get(directUser) ?: setOf()
                directEventContent.value =
                    currentDirectRooms?.copy(currentDirectRooms.mappings + (directUser to (existingDirectRoomsWithUser + roomId)))
                        ?: DirectEventContent(mapOf(directUser to setOf(roomId)))
            }
            if ((event.content.membership == LEAVE || event.content.membership == BAN) && currentDirectRooms != null) {
                if (directUser != userInfo.userId) {
                    log.debug { "unmark room $roomId as direct room with $directUser" }
                    directEventContent.value = DirectEventContent(
                        (currentDirectRooms.mappings + (directUser to (currentDirectRooms.mappings[directUser].orEmpty() - roomId)))
                            .filterValues { it.isNullOrEmpty().not() }
                    )
                } else {
                    log.debug { "remove room $roomId from direct rooms, because we left it" }
                    directEventContent.value = DirectEventContent(
                        currentDirectRooms.mappings.mapValues { it.value?.minus(roomId) }
                            .filterValues { it.isNullOrEmpty().not() }
                    )
                }
            }
        }
        val finalNewDirectRooms = directEventContent.value
        if (finalNewDirectRooms != null && finalNewDirectRooms != initialDirectEventContent) {
            api.user.setAccountData(finalNewDirectRooms, userInfo.userId).getOrThrow()
        }
    }

    internal suspend fun setDirectRoomProperties(directEvent: ClientEvent<DirectEventContent>) {
        val allDirectRooms = directEvent.content.mappings.entries
            .flatMap { entry -> entry.value?.map { it to entry.key }.orEmpty() }
            .groupBy { it.first }
            .mapValues { entry -> entry.value.map { it.second } }

        roomStore.getAll().first().keys.map { room ->
            val directUser = allDirectRooms[room]?.first()
            val avatarUrl =
                if (directUser != null && roomStateStore.getByStateKey<AvatarEventContent>(room)
                        .first()?.content?.url.isNullOrEmpty()
                )
                    roomStateStore.getByStateKey<MemberEventContent>(room, stateKey = directUser.full).first()
                        ?.content?.avatarUrl
                else null
            roomStore.update(room) { oldRoom ->
                oldRoom?.copy(
                    avatarUrl = avatarUrl?.ifEmpty { null } ?: oldRoom.avatarUrl,
                    isDirect = directUser != null
                )
            }
        }
    }
}