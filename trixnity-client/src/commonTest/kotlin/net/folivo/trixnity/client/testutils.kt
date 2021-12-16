package net.folivo.trixnity.client

import kotlinx.datetime.Clock
import net.folivo.trixnity.client.store.RepositoryTransactionManager
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

val simpleRoom =
    Room(RoomId("room", "server"), lastMessageEventAt = Clock.System.now(), lastEventId = EventId("\$event"))

object NoopRepositoryTransactionManager : RepositoryTransactionManager {
    override suspend fun <T> transaction(block: suspend () -> T): T = block()
}