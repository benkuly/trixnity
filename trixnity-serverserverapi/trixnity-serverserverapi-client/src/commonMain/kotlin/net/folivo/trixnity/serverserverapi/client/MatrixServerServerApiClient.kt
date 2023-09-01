package net.folivo.trixnity.serverserverapi.client

import io.ktor.client.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.client.MatrixApiClient
import net.folivo.trixnity.api.client.defaultTrixnityHttpClient
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createMatrixEventAndDataUnitJson
import net.folivo.trixnity.core.serialization.events.DefaultDataUnitContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.GetRoomVersionFunction

interface MatrixServerServerApiClient {
    val httpClient: MatrixApiClient

    val discovery: DiscoveryApiClient
    val federation: FederationApiClient
}

class MatrixServerServerApiClientImpl(
    hostname: String,
    getDelegatedDestination: (String, Int) -> Pair<String, Int>,
    sign: (String) -> Key.Ed25519Key,
    getRoomVersion: GetRoomVersionFunction,
    private val eventContentSerializerMappings: EventContentSerializerMappings = DefaultDataUnitContentSerializerMappings,
    private val json: Json = createMatrixEventAndDataUnitJson(getRoomVersion, eventContentSerializerMappings),
    httpClientFactory: (config: HttpClientConfig<*>.() -> Unit) -> HttpClient = { defaultTrixnityHttpClient(config=it) },
) : MatrixServerServerApiClient {
    override val httpClient = MatrixApiClient(eventContentSerializerMappings, json) {
        httpClientFactory {
            it()
            install(MatrixSignatureAuthPlugin(hostname, sign, json))
            install(MatrixDestinationPlugin(getDelegatedDestination))
        }
    }

    override val discovery = DiscoveryApiClientImpl(httpClient)
    override val federation = FederationApiClientImpl(httpClient)
}