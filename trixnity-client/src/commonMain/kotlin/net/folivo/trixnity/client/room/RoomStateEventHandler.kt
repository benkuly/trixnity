package net.folivo.trixnity.client.room

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import net.folivo.trixnity.client.store.RoomStateStore
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.user.LazyMemberEventHandler
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.ClientEvent.StateBaseEvent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.subscribeEventList
import net.folivo.trixnity.core.unsubscribeOnCompletion

private val log = KotlinLogging.logger { }

class RoomStateEventHandler(
    private val api: MatrixClientServerApiClient,
    private val roomStateStore: RoomStateStore,
    private val tm: RepositoryTransactionManager,
) : EventHandler, LazyMemberEventHandler {
    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeEventList<StateEventContent, StateBaseEvent<StateEventContent>> { setState(it) }
            .unsubscribeOnCompletion(scope)
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