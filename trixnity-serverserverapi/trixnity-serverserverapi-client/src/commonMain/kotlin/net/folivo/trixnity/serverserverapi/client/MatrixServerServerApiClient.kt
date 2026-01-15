package net.folivo.trixnity.serverserverapi.client

import io.ktor.client.*
import io.ktor.client.engine.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.client.MatrixApiClient
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createMatrixEventAndDataUnitJson
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.RoomVersionStore
import net.folivo.trixnity.core.serialization.events.defaultDataUnit

interface MatrixServerServerApiClient : AutoCloseable {
    val baseClient: MatrixApiClient
    val discovery: DiscoveryApiClient
    val federation: FederationApiClient
}

class MatrixServerServerApiClientImpl(
    hostname: String,
    getDelegatedDestination: (String, Int) -> Pair<String, Int>,
    sign: (String) -> Key.Ed25519Key,
    roomVersionStore: RoomVersionStore,
    private val eventContentSerializerMappings: EventContentSerializerMappings = EventContentSerializerMappings.defaultDataUnit,
    json: Json = createMatrixEventAndDataUnitJson(roomVersionStore, eventContentSerializerMappings),
    httpClientEngine: HttpClientEngine? = null,
    httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
) : MatrixServerServerApiClient {
    override val baseClient = MatrixApiClient(eventContentSerializerMappings, json, httpClientEngine) {
        install(MatrixSignatureAuthPlugin(hostname, sign))
        install(MatrixDestinationPlugin(getDelegatedDestination))
        install(ConvertMediaPlugin)

        httpClientConfig?.invoke(this)
    }

    override val discovery = DiscoveryApiClientImpl(baseClient)
    override val federation = FederationApiClientImpl(baseClient)

    override fun close() {
        baseClient.close()
    }
}