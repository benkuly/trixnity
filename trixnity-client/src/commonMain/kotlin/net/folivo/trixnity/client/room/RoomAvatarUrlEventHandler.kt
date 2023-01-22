package net.folivo.trixnity.client.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import mu.KotlinLogging
import net.folivo.trixnity.client.getRoomId
import net.folivo.trixnity.client.getStateKey
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.room.AvatarEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribe

private val log = KotlinLogging.logger {}

class RoomAvatarUrlEventHandler(
    private val userInfo: UserInfo,
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val globalAccountDataStore: GlobalAccountDataStore,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(::setAvatarUrlForMemberUpdates)
        api.sync.subscribe(::setAvatarUrlForAvatarEvents)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.unsubscribe(::setAvatarUrlForMemberUpdates)
            api.sync.unsubscribe(::setAvatarUrlForAvatarEvents)
        }
    }

    internal suspend fun setAvatarUrlForMemberUpdates(memberEvent: Event<MemberEventContent>) {
        memberEvent.getRoomId()?.let { roomId ->
            val room = roomStore.get(roomId).first()
            if (room?.isDirect == true && userInfo.userId.full != memberEvent.getStateKey()) {
                log.debug { "set room avatar of room $roomId due to member update" }
                roomStore.update(roomId) { oldRoom ->
                    oldRoom?.copy(avatarUrl = memberEvent.content.avatarUrl?.ifEmpty { null })
                }
            }
        }
    }

    internal suspend fun setAvatarUrlForAvatarEvents(avatarEvent: Event<AvatarEventContent>) {
        avatarEvent.getRoomId()?.let { roomId ->
            log.debug { "set room avatar of room $roomId due to new avatar event" }
            val avatarUrl = avatarEvent.content.url
            val room = roomStore.get(roomId).first()
            if (room?.isDirect?.not() == true || avatarUrl.isNullOrEmpty().not()) {
                roomStore.update(roomId) { oldRoom -> oldRoom?.copy(avatarUrl = avatarUrl?.ifEmpty { null }) }
            } else if (avatarUrl.isNullOrEmpty()) {
                globalAccountDataStore.get<DirectEventContent>().first()?.content?.mappings?.let { mappings ->
                    mappings.entries.forEach { (userId, rooms) ->
                        rooms
                            ?.filter { room -> room == roomId }
                            ?.forEach { room ->
                                val newAvatarUrl =
                                    roomStateStore.getByStateKey<MemberEventContent>(room, stateKey = userId.full)
                                        .first()?.content?.avatarUrl
                                roomStore.update(room) { oldRoom ->
                                    oldRoom?.copy(avatarUrl = newAvatarUrl?.ifEmpty { null })
                                }
                            }
                    }
                }
            }
        }
    }
}