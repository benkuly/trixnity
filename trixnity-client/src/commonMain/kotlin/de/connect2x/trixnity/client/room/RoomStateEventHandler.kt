package de.connect2x.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import de.connect2x.trixnity.client.store.RoomStateStore
import de.connect2x.trixnity.client.store.TransactionManager
import de.connect2x.trixnity.client.user.LazyMemberEventHandler
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.core.ClientEventEmitter.Priority
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.StateBaseEvent
import de.connect2x.trixnity.core.model.events.StateEventContent
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.subscribeEventList
import de.connect2x.trixnity.core.unsubscribeOnCompletion

private val log = KotlinLogging.logger("de.connect2x.trixnity.client.room.RoomStateEventHandler")

class RoomStateEventHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStateStore: RoomStateStore,
    private val tm: TransactionManager,
) : EventHandler, LazyMemberEventHandler {
    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeEventList<StateEventContent, StateBaseEvent<StateEventContent>>(Priority.STORE_EVENTS) {
            setState(it)
        }.unsubscribeOnCompletion(scope)
    }

    override suspend fun handleLazyMemberEvents(memberEvents: List<StateEvent<MemberEventContent>>) {
        setState(memberEvents, skipWhenAlreadyPresent = true)
    }

    internal suspend fun setState(
        events: List<StateBaseEvent<*>>,
        skipWhenAlreadyPresent: Boolean = false
    ) {
        if (events.isNotEmpty()) {
            log.debug { "start save ${events.size} state events" }
            tm.writeTransaction {
                events.forEach {
                    roomStateStore.save(it, skipWhenAlreadyPresent)
                }
            }
            log.debug { "finished save ${events.size} state events" }
        }
    }
}