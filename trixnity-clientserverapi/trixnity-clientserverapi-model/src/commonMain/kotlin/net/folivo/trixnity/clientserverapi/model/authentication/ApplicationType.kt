package net.folivo.trixnity.clientserverapi.model.authentication

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ApplicationType {
    @SerialName("web")
    WEB,

    @SerialName("native")
    NATIVE
}
