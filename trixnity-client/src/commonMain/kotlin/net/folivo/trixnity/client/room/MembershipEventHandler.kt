package net.folivo.trixnity.client.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import mu.KotlinLogging
import net.folivo.trixnity.client.getRoomId
import net.folivo.trixnity.client.getStateKey
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.client.store.RoomStore
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribe

private val log = KotlinLogging.logger {}

class MembershipEventHandler(
    private val userInfo: UserInfo,
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(::setOwnMembership)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.unsubscribe(::setOwnMembership)
        }
    }

    internal suspend fun setOwnMembership(event: Event<MemberEventContent>) {
        val roomId = event.getRoomId()
        val stateKey = event.getStateKey()
        if (roomId != null && stateKey != null && stateKey == userInfo.userId.full) {
            log.debug { "set own membership of room $roomId" }
            roomStore.update(roomId) { oldRoom ->
                oldRoom?.copy(
                    membership = event.content.membership
                ) ?: Room(
                    roomId = roomId,
                    membership = event.content.membership,
                )
            }
        }
    }
}