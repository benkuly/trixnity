package net.folivo.trixnity.client.key

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
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
    private val api: MatrixClientServerApiClient,
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
        if (event is Event.StateEvent && roomStore.get(event.roomId)
                .first()?.encryptionAlgorithm == EncryptionAlgorithm.Megolm
        ) {
            log.trace { "handle membership change in an encrypted room" }
            val userId = UserId(event.stateKey)
            when (event.content.membership) {
                Membership.LEAVE, Membership.BAN -> {
                    if (roomStore.encryptedJoinedRooms().find { roomId ->
                            roomStateStore.getByStateKey<MemberEventContent>(roomId, event.stateKey).first()
                                ?.content?.membership.let { it == Membership.JOIN || it == Membership.INVITE }
                        } == null)
                        keyStore.updateDeviceKeys(userId) { null }
                }

                Membership.JOIN, Membership.INVITE -> {
                    if (event.unsigned?.previousContent?.membership != event.content.membership
                        && keyStore.getDeviceKeys(userId).first() == null
                    ) keyStore.updateOutdatedKeys { it + userId }
                }

                else -> {
                }
            }
        }
    }
}