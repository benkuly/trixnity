package net.folivo.trixnity.client.user

import kotlinx.coroutines.CoroutineScope
import net.folivo.trixnity.client.store.TransactionManager
import net.folivo.trixnity.client.store.UserPresence
import net.folivo.trixnity.client.store.UserPresenceStore
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.events.senderOrNull
import net.folivo.trixnity.core.subscribeEventList
import net.folivo.trixnity.core.unsubscribeOnCompletion
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

class UserPresenceEventHandler(
    private val userPresenceStore: UserPresenceStore,
    private val tm: TransactionManager,
    private val clock: Clock,
    private val api: MatrixClientServerApiClient,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeEventList(subscriber = ::setPresence).unsubscribeOnCompletion(scope)
    }

    internal suspend fun setPresence(presenceEvents: List<ClientEvent.EphemeralEvent<PresenceEventContent>>) {
        if (presenceEvents.isNotEmpty()) {
            val now = clock.now()
            val newUserPresences = presenceEvents.mapNotNull { presenceEvent ->
                presenceEvent.senderOrNull?.let { sender ->
                    sender to with(presenceEvent.content) {
                        UserPresence(
                            presence = presence,
                            lastUpdate = now,
                            lastActive = lastActiveAgo?.let { now - it.milliseconds },
                            isCurrentlyActive = isCurrentlyActive,
                            statusMessage = statusMessage
                        )
                    }
                }
            }
            tm.writeTransaction {
                newUserPresences.forEach { (userId, userPresence) ->
                    userPresenceStore.setPresence(userId, userPresence)
                }
            }
        }
    }
}