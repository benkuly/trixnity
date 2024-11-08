package net.folivo.trixnity.serverserverapi.client

import io.ktor.client.*
import io.ktor.client.engine.*
import kotlinx.serialization.json.Json
import net.folivo.trixnity.api.client.MatrixApiClient
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createMatrixEventAndDataUnitJson
import net.folivo.trixnity.core.serialization.events.DefaultDataUnitContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.GetRoomVersionFunction

interface MatrixServerServerApiClient : AutoCloseable {
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
    httpClientEngine: HttpClientEngine? = null,
    httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
) : MatrixServerServerApiClient {
    internal val httpClient = MatrixApiClient(eventContentSerializerMappings, json, httpClientEngine) {
        install(MatrixSignatureAuthPlugin(hostname, sign, json))
        install(MatrixDestinationPlugin(getDelegatedDestination))
        install(ConvertMediaPlugin)

        httpClientConfig?.invoke(this)
    }

    override val discovery = DiscoveryApiClientImpl(httpClient)
    override val federation = FederationApiClientImpl(httpClient)

    override fun close() {
        httpClient.close()
    }
}