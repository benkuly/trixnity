package net.folivo.trixnity.clientserverapi.model.server

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint
import net.folivo.trixnity.core.HttpMethod

@Serializable
@Resource("/_matrix/client/v3/capabilities")
@HttpMethod(GET)
object GetCapabilities : MatrixEndpoint<Unit, GetCapabilities.Response> {
    @Serializable
    data class Response(
        @SerialName("capabilities") val capabilities: Capabilities
    ) {
        @Serializable
        data class Capabilities(
            @SerialName("m.change_password") val changePassword: ChangePasswordCapability,
            @SerialName("m.room_versions") val roomVersion: RoomVersionsCapability
        ) {
            @Serializable
            data class ChangePasswordCapability(
                @SerialName("enabled") val enabled: Boolean
            )

            @Serializable
            data class RoomVersionsCapability(
                @SerialName("default") val default: String,
                @SerialName("available") val available: Map<String, RoomVersionStability>
            ) {
                @Serializable
                enum class RoomVersionStability {
                    @SerialName("stable")
                    STABLE,

                    @SerialName("unstable")
                    UNSTABLE
                }
            }
        }
    }
}