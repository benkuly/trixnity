package net.folivo.trixnity.clientserverapi.model.server

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.core.MatrixJsonEndpoint

@Serializable
@Resource("/_matrix/client/v3/capabilities")
object GetCapabilities : MatrixJsonEndpoint<Unit, GetCapabilities.Response>() {
    @Transient
    override val method = Get

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