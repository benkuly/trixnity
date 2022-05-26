package net.folivo.trixnity.serverserverapi.client

import io.ktor.client.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.client.MatrixApiClient
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createEphemeralDateUnitContentSerializerMappings
import net.folivo.trixnity.core.serialization.createEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.createMatrixEventAndDataUnitJson
import net.folivo.trixnity.core.serialization.events.EphemeralDataUnitContentMappings
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
    val eventContentSerializerMappings: EventContentSerializerMappings = createEventContentSerializerMappings(),
    val ephemeralMappings: EphemeralDataUnitContentMappings = createEphemeralDateUnitContentSerializerMappings(),
    val json: Json =
        createMatrixEventAndDataUnitJson(getRoomVersion, eventContentSerializerMappings, ephemeralMappings),
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