package net.folivo.trixnity.client.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    @SerialName("errcode")
    val errorCode: String,
    @SerialName("error")
    val errorMessage: String? = null
)