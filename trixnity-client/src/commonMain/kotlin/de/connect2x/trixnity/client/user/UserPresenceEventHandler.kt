package de.connect2x.trixnity.client.user

import kotlinx.coroutines.CoroutineScope
import de.connect2x.trixnity.client.store.TransactionManager
import de.connect2x.trixnity.client.store.UserPresence
import de.connect2x.trixnity.client.store.UserPresenceStore
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.PresenceEventContent
import de.connect2x.trixnity.core.model.events.senderOrNull
import de.connect2x.trixnity.core.subscribeEventList
import de.connect2x.trixnity.core.unsubscribeOnCompletion
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