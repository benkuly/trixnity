package net.folivo.trixnity.serverserverapi.client

import io.ktor.client.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.client.MatrixApiClient
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

interface IMatrixServerServerApiClient {
    val httpClient: MatrixApiClient

    val discovery: IDiscoveryApiClient
    val federation: IFederationApiClient
}

class MatrixServerServerApiClient(
    private val hostname: String,
    private val getDelegatedDestination: (String, Int) -> Pair<String, Int>,
    private val sign: (String) -> Key.Ed25519Key,
    val eventContentSerializerMappings: EventContentSerializerMappings = createEventContentSerializerMappings(),
    val json: Json = createMatrixEventJson(eventContentSerializerMappings),
    httpClientFactory: (HttpClientConfig<*>.() -> Unit) -> HttpClient = { HttpClient(it) },
) : IMatrixServerServerApiClient {
    override val httpClient = MatrixApiClient(json, eventContentSerializerMappings) {
        httpClientFactory {
            it()
            install(MatrixSignatureAuthPlugin(hostname, sign, json))
            install(MatrixDestinationPlugin(getDelegatedDestination))
        }
    }

    override val discovery = DiscoveryApiClient(httpClient)
    override val federation = FederationApiClient(httpClient)
}