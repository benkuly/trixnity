package net.folivo.trixnity.clientserverapi.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CapabilitiesResponse(
    @SerialName("capabilities") val capabilities: Capabilities
) {
    @Serializable
    data class Capabilities(
        @SerialName("m.change_password") val changePassword: ChangePasswordCapability,
        @SerialName("m.room_versions") val roomVersion: RoomVersionsCapability
    )

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