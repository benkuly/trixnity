package net.folivo.trixnity.clientserverapi.model.server

import io.ktor.resources.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.HttpMethod
import net.folivo.trixnity.core.HttpMethodType.GET
import net.folivo.trixnity.core.MatrixEndpoint

/**
 * @see <a href="https://spec.matrix.org/v1.3/client-server-api/#get_matrixclientv3capabilities">matrix spec</a>
 */
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
            @SerialName("m.change_password")
            val changePassword: ChangePasswordCapability = ChangePasswordCapability(true),
            @SerialName("m.room_versions")
            val roomVersion: RoomVersionsCapability? = null,
            @SerialName("m.set_displayname")
            val setDisplayName: SetDisplayNameCapability = SetDisplayNameCapability(true),
            @SerialName("m.set_avatar_url")
            val setAvatarUrl: SetAvatarUrlCapability = SetAvatarUrlCapability(true),
            @SerialName("m.3pid_changes")
            val thirdPartyChanges: ThirdPartyChangesCapability = ThirdPartyChangesCapability(true),
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

            @Serializable
            data class SetDisplayNameCapability(
                @SerialName("enabled") val enabled: Boolean
            )

            @Serializable
            data class SetAvatarUrlCapability(
                @SerialName("enabled") val enabled: Boolean
            )

            @Serializable
            data class ThirdPartyChangesCapability(
                @SerialName("enabled") val enabled: Boolean
            )
        }
    }
}