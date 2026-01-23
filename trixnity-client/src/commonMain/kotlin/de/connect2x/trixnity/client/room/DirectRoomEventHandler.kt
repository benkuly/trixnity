package de.connect2x.trixnity.client.room

import de.connect2x.lognity.api.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import de.connect2x.trixnity.client.store.GlobalAccountDataStore
import de.connect2x.trixnity.client.store.get
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import de.connect2x.trixnity.core.model.events.m.DirectEventContent
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership.BAN
import de.connect2x.trixnity.core.model.events.m.room.Membership.LEAVE
import de.connect2x.trixnity.core.subscribeEventList
import de.connect2x.trixnity.core.unsubscribeOnCompletion

private val log = Logger("de.connect2x.trixnity.client.room.DirectRoomEventHandler")

class DirectRoomEventHandler(
    private val userInfo: UserInfo,
    private val api: MatrixClientServerApiClient,
    private val globalAccountDataStore: GlobalAccountDataStore,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeEventList(subscriber = ::setNewDirectEventFromMemberEvent).unsubscribeOnCompletion(scope)
    }

    internal suspend fun setNewDirectEventFromMemberEvent(events: List<ClientEvent.StateBaseEvent<MemberEventContent>>) {
        if (events.isNotEmpty()) {
            val initialDirectEventContentMappings =
                globalAccountDataStore.get<DirectEventContent>().first()?.content?.mappings.orEmpty()

            log.trace { "direct event mappings before recalculation: $initialDirectEventContentMappings" }

            var directEventContentMappings = initialDirectEventContentMappings
            for (event in events) {
                val roomId = event.roomId ?: continue // in sync, roomId is always there
                val stateKey = event.stateKey
                val sender = event.sender
                val userWithMembershipChange = UserId(stateKey)

                if (userWithMembershipChange == userInfo.userId && (event.content.membership == LEAVE || event.content.membership == BAN)) {
                    log.debug { "remove room $roomId from direct rooms, because we left it" }
                    directEventContentMappings =
                        directEventContentMappings.mapValues { it.value?.minus(roomId) }
                            .filterValues { it.isNullOrEmpty().not() }
                } else if (event.content.isDirect == true && !(userInfo.userId == sender && sender == userWithMembershipChange)) {
                    val directUser = if (userInfo.userId == sender) userWithMembershipChange else sender
                    log.debug { "mark room $roomId as direct room with $directUser" }
                    directEventContentMappings =
                        directEventContentMappings + (directUser to (directEventContentMappings[directUser].orEmpty() + roomId))
                }
            }

            log.trace { "direct event mappings after recalculation: $directEventContentMappings" }

            if (directEventContentMappings != initialDirectEventContentMappings) {
                api.user.setAccountData(DirectEventContent(directEventContentMappings), userInfo.userId).getOrThrow()
            }
        }
    }
}