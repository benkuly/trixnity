package net.folivo.trixnity.client.key

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import mu.KotlinLogging
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import net.folivo.trixnity.core.subscribe
import net.folivo.trixnity.core.unsubscribe
import net.folivo.trixnity.crypto.olm.membershipsAllowedToReceiveKey

private val log = KotlinLogging.logger {}

class KeyEncryptionEventHandler(
    private val api: MatrixClientServerApiClient,
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
            val allowedMemberships =
                roomStateStore.getByStateKey<HistoryVisibilityEventContent>(event.roomId)
                    .first()?.content?.historyVisibility
                    .membershipsAllowedToReceiveKey
            val outdatedKeys = roomStateStore.members(event.roomId, allowedMemberships).filterNot {
                keyStore.isTracked(it)
            }
            keyStore.updateOutdatedKeys { it + outdatedKeys }
        }
    }
}