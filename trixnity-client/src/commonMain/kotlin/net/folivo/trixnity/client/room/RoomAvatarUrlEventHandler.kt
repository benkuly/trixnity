package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import net.folivo.trixnity.client.getRoomId
import net.folivo.trixnity.client.getStateKey
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.utils.filter
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.room.AvatarEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent

private val log = KotlinLogging.logger {}

class RoomAvatarUrlEventHandler(
    private val userInfo: UserInfo,
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val globalAccountDataStore: GlobalAccountDataStore,
    private val tm: RepositoryTransactionManager,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.afterSyncResponse.subscribe(::handleSyncResponse)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.afterSyncResponse.unsubscribe(::handleSyncResponse)
        }
    }

    internal suspend fun handleSyncResponse(syncResponse: Sync.Response) {
        setAvatarUrlForMemberUpdates(
            syncResponse.filter<MemberEventContent>().filterIsInstance<Event<MemberEventContent>>().toList()
        )
        setAvatarUrlForAvatarEvents(
            syncResponse.filter<AvatarEventContent>().filterIsInstance<Event<AvatarEventContent>>().toList()
        )
    }

    internal suspend fun setAvatarUrlForMemberUpdates(memberEvents: List<Event<MemberEventContent>>) {
        val newAvatarUrls = memberEvents.mapNotNull { memberEvent ->
            memberEvent.getRoomId()?.let { roomId ->
                val room = roomStore.get(roomId).first()
                if (room?.isDirect == true && userInfo.userId.full != memberEvent.getStateKey()) {
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

    internal suspend fun setAvatarUrlForAvatarEvents(avatarEvents: List<Event<AvatarEventContent>>) {
        val newAvatarUrls = avatarEvents.mapNotNull { avatarEvent ->
            avatarEvent.getRoomId()?.let { roomId ->
                log.debug { "set room avatar of room $roomId due to new avatar event" }
                val avatarUrl = avatarEvent.content.url
                val room = roomStore.get(roomId).first()
                if (room?.isDirect?.not() == true || avatarUrl.isNullOrEmpty().not()) {
                    listOf(roomId to avatarUrl?.ifEmpty { null })
                } else if (avatarUrl.isNullOrEmpty()) {
                    globalAccountDataStore.get<DirectEventContent>().first()?.content?.mappings?.let { mappings ->
                        mappings.entries.mapNotNull { (userId, rooms) ->
                            rooms
                                ?.filter { room -> room == roomId }
                                ?.map { room ->
                                    val newAvatarUrl =
                                        roomStateStore.getByStateKey<MemberEventContent>(room, stateKey = userId.full)
                                            .first()?.content?.avatarUrl
                                    room to newAvatarUrl?.ifEmpty { null }
                                }
                        }.flatten()
                    }
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