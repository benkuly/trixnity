package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import net.folivo.trixnity.client.getStateKey
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.utils.filterContent
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncProcessingData
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.room.AvatarEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.roomIdOrNull
import net.folivo.trixnity.core.model.events.stateKeyOrNull

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
        api.sync.afterSyncProcessing.subscribe(::handleSyncResponse)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.afterSyncProcessing.unsubscribe(::handleSyncResponse)
        }
    }

    internal suspend fun handleSyncResponse(syncProcessingData: SyncProcessingData) {
        setAvatarUrlForMemberUpdates(
            syncProcessingData.allEvents.filterContent<MemberEventContent>().toList()
        )
        setAvatarUrlForAvatarEvents(
            syncProcessingData.allEvents.filterContent<AvatarEventContent>().toList()
        )
    }

    internal suspend fun setAvatarUrlForMemberUpdates(memberEvents: List<Event<MemberEventContent>>) {
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

    internal suspend fun setAvatarUrlForAvatarEvents(avatarEvents: List<Event<AvatarEventContent>>) {
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