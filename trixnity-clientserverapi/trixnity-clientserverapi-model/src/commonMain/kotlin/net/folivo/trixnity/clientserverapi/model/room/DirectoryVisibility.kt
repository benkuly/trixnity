package net.folivo.trixnity.clientserverapi.model.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DirectoryVisibility {
    @SerialName("public")
    PUBLIC,

    @SerialName("private")
    PRIVATE
}