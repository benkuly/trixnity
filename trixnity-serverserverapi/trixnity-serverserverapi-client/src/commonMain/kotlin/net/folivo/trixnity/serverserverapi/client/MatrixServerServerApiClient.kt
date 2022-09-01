package net.folivo.trixnity.serverserverapi.client

import io.ktor.client.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.client.MatrixApiClient
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createMatrixEventAndDataUnitJson
import net.folivo.trixnity.core.serialization.events.DefaultDataUnitContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.GetRoomVersionFunction

interface IMatrixServerServerApiClient {
    val httpClient: MatrixApiClient

    val discovery: IDiscoveryApiClient
    val federation: IFederationApiClient
}

class MatrixServerServerApiClient(
    hostname: String,
    getDelegatedDestination: (String, Int) -> Pair<String, Int>,
    sign: (String) -> Key.Ed25519Key,
    getRoomVersion: GetRoomVersionFunction,
    private val eventContentSerializerMappings: EventContentSerializerMappings = DefaultDataUnitContentSerializerMappings,
    private val json: Json = createMatrixEventAndDataUnitJson(getRoomVersion, eventContentSerializerMappings),
    httpClientFactory: (HttpClientConfig<*>.() -> Unit) -> HttpClient = { HttpClient(it) },
) : IMatrixServerServerApiClient {
    override val httpClient = MatrixApiClient(eventContentSerializerMappings, json) {
        httpClientFactory {
            it()
            install(MatrixSignatureAuthPlugin(hostname, sign, json))
            install(MatrixDestinationPlugin(getDelegatedDestination))
        }
    }

    override val discovery = DiscoveryApiClient(httpClient)
    override val federation = FederationApiClient(httpClient)
}