package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.store.GlobalAccountDataStore
import net.folivo.trixnity.client.store.get
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership.BAN
import net.folivo.trixnity.core.model.events.m.room.Membership.LEAVE
import net.folivo.trixnity.core.subscribeEventList
import net.folivo.trixnity.core.unsubscribeOnCompletion

private val log = KotlinLogging.logger {}

class DirectRoomEventHandler(
    private val userInfo: UserInfo,
    private val api: MatrixClientServerApiClient,
    private val globalAccountDataStore: GlobalAccountDataStore,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeEventList(subscriber = ::setNewDirectEventFromMemberEvent).unsubscribeOnCompletion(scope)
    }

    internal suspend fun setNewDirectEventFromMemberEvent(events: List<StateEvent<MemberEventContent>>) {
        if (events.isNotEmpty()) {
            val initialDirectEventContentMappings =
                globalAccountDataStore.get<DirectEventContent>().first()?.content?.mappings.orEmpty()

            log.trace { "direct event mappings before recalculation: $initialDirectEventContentMappings" }

            var directEventContentMappings = initialDirectEventContentMappings
            for (event in events) {
                val roomId = event.roomId
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