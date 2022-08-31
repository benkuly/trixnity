package net.folivo.trixnity.client.key

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import mu.KotlinLogging
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.client.store.RoomStateStore
import net.folivo.trixnity.client.store.isTracked
import net.folivo.trixnity.client.store.members
import net.folivo.trixnity.clientserverapi.client.IMatrixClientServerApiClient
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribe

private val log = KotlinLogging.logger {}

class KeyEncryptionEventHandler(
    private val api: IMatrixClientServerApiClient,
    private val roomStateStore: RoomStateStore,
    private val keyStore: KeyStore,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(::handleEncryptionEvents)
        scope.coroutineContext.job.invokeOnCompletion {
            api.sync.unsubscribe(::handleEncryptionEvents)
        }
    }

    internal suspend fun handleEncryptionEvents(event: Event<EncryptionEventContent>) {
        if (event is Event.StateEvent) {
            log.debug { "handle EncryptionEvents" }
            val outdatedKeys = roomStateStore.members(event.roomId, Membership.JOIN, Membership.INVITE).filterNot {
                keyStore.isTracked(it)
            }
            keyStore.outdatedKeys.update { it + outdatedKeys }
        }
    }
}