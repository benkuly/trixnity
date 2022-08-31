package net.folivo.trixnity.client.key

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import mu.KotlinLogging
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.IMatrixClientServerApiClient
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribe

private val log = KotlinLogging.logger {}

class KeyMemberEventHandler(
    private val api: IMatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val roomStateStore: RoomStateStore,
    private val keyStore: KeyStore,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(::handleMemberEvents)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.unsubscribe(::handleMemberEvents)
        }
    }

    internal suspend fun handleMemberEvents(event: Event<MemberEventContent>) {
        if (event is Event.StateEvent && roomStore.get(event.roomId).value?.encryptionAlgorithm == EncryptionAlgorithm.Megolm) {
            log.debug { "handle membership change in an encrypted room" }
            val userId = UserId(event.stateKey)
            when (event.content.membership) {
                Membership.LEAVE, Membership.BAN -> {
                    if (roomStore.encryptedJoinedRooms().find { roomId ->
                            roomStateStore.getByStateKey<MemberEventContent>(roomId, event.stateKey)
                                ?.content?.membership.let { it == Membership.JOIN || it == Membership.INVITE }
                        } == null)
                        keyStore.updateDeviceKeys(userId) { null }
                }

                Membership.JOIN, Membership.INVITE -> {
                    if (event.unsigned?.previousContent?.membership != event.content.membership
                        && keyStore.getDeviceKeys(userId) == null
                    ) keyStore.outdatedKeys.update { it + userId }
                }

                else -> {
                }
            }
        }
    }
}