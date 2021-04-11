package net.folivo.trixnity.appservice.rest.api.room

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Visibility {
    @SerialName("public")
    PUBLIC,

    @SerialName("private")
    PRIVATE
}