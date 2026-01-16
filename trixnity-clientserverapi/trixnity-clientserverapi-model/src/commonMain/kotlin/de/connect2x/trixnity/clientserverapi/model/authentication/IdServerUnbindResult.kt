package de.connect2x.trixnity.clientserverapi.model.authentication

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class IdServerUnbindResult {
    @SerialName("success")
    SUCCESS,

    @SerialName("no-support")
    NOSUPPORT
}