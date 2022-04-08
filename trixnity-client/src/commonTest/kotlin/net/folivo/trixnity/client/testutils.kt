package net.folivo.trixnity.client

import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.RepositoryTransactionManager
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.configurePortableMockEngine
import net.folivo.trixnity.testutils.mockEngineFactory

val simpleRoom =
    Room(RoomId("room", "server"), lastMessageEventAt = Clock.System.now(), lastEventId = EventId("\$event"))

object NoopRepositoryTransactionManager : RepositoryTransactionManager {
    override suspend fun <T> transaction(block: suspend () -> T): T = block()
}

fun mockMatrixClientServerApiClient(json: Json): Pair<MatrixClientServerApiClient, PortableMockEngineConfig> {
    val config = PortableMockEngineConfig()
    val api = MatrixClientServerApiClient(
        json = json,
        httpClientFactory = mockEngineFactory { configurePortableMockEngine(config) }
    )
    return api to config
}