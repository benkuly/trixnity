package net.folivo.trixnity.clientserverapi.model.discovery

import io.ktor.resources.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.folivo.trixnity.clientserverapi.model.authentication.DiscoveryInformation
import net.folivo.trixnity.core.Auth
import net.folivo.trixnity.core.AuthRequired
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#getwell-knownmatrixclient">matrix spec</a>
 */
@Serializable
@Resource("/.well-known/matrix/client")
@HttpMethod(GET)
@Auth(AuthRequired.NO)
object GetWellKnown : MatrixEndpoint<Unit, DiscoveryInformation> {
    override fun responseSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json,
        value: DiscoveryInformation?
    ): KSerializer<DiscoveryInformation>? = DiscoveryInformation.serializer()
}