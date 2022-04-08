package net.folivo.trixnity.clientserverapi.model.rooms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DirectoryVisibility {
    @SerialName("public")
    PUBLIC,

    @SerialName("private")
    PRIVATE
}