package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.room.AvatarEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.roomIdOrNull
import net.folivo.trixnity.core.model.events.stateKeyOrNull
import net.folivo.trixnity.core.subscribeEventList
import net.folivo.trixnity.core.unsubscribeOnCompletion

private val log = KotlinLogging.logger {}

// TODO merge into RoomListHandler (performance reasons)
class RoomAvatarUrlEventHandler(
    private val userInfo: UserInfo,
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val globalAccountDataStore: GlobalAccountDataStore,
    private val tm: TransactionManager,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeEventList(subscriber = ::setAvatarUrlForMemberUpdates).unsubscribeOnCompletion(scope)
        api.sync.subscribeEventList(subscriber = ::setAvatarUrlForAvatarEvents).unsubscribeOnCompletion(scope)
    }

    internal suspend fun setAvatarUrlForMemberUpdates(memberEvents: List<ClientEvent<MemberEventContent>>) {
        val newAvatarUrls = memberEvents.mapNotNull { memberEvent ->
            memberEvent.roomIdOrNull?.let { roomId ->
                val room = roomStore.get(roomId).first()
                if (room?.isDirect == true && userInfo.userId.full != memberEvent.stateKeyOrNull) {
                    log.debug { "set room avatar of room $roomId due to member update" }
                    roomId to memberEvent.content.avatarUrl?.ifEmpty { null }
                } else null
            }
        }
        if (newAvatarUrls.isNotEmpty())
            tm.writeTransaction {
                newAvatarUrls.forEach { (roomId, avatarUrl) ->
                    roomStore.update(roomId) { oldRoom ->
                        oldRoom?.copy(avatarUrl = avatarUrl)
                    }
                }
            }
    }

    internal suspend fun setAvatarUrlForAvatarEvents(avatarEvents: List<ClientEvent<AvatarEventContent>>) {
        val newAvatarUrls = avatarEvents.mapNotNull { avatarEvent ->
            avatarEvent.roomIdOrNull?.let { roomId ->
                log.debug { "set room avatar of room $roomId due to new avatar event" }
                val avatarUrl = avatarEvent.content.url
                val room = roomStore.get(roomId).first()
                if (room?.isDirect?.not() == true || avatarUrl.isNullOrEmpty().not()) {
                    listOf(roomId to avatarUrl?.ifEmpty { null })
                } else if (avatarUrl.isNullOrEmpty()) {
                    globalAccountDataStore.get<DirectEventContent>().first()?.content?.mappings?.entries
                        ?.mapNotNull { (userId, rooms) ->
                            rooms
                                ?.filter { room -> room == roomId }
                                ?.map { room ->
                                    val newAvatarUrl =
                                        roomStateStore.getByStateKey<MemberEventContent>(room, stateKey = userId.full)
                                            .first()?.content?.avatarUrl
                                    room to newAvatarUrl?.ifEmpty { null }
                                }
                        }?.flatten()
                } else null
            }
        }.flatten()
        if (newAvatarUrls.isNotEmpty())
            tm.writeTransaction {
                newAvatarUrls.forEach { (roomId, avatarUrl) ->
                    roomStore.update(roomId) { oldRoom ->
                        oldRoom?.copy(avatarUrl = avatarUrl)
                    }
                }
            }
    }
}