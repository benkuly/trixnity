package net.folivo.trixnity.clientserverapi.model.authentication.oauth2.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuth2ErrorException(
    val error: OAuth2ErrorType,
    val description: String? = null,
    @SerialName("error_uri") val errorUri: String? = null,
    val state: String? = null
) : RuntimeException(description) {
    override fun equals(other: Any?): Boolean {
        if (other !is OAuth2ErrorException) return false
        return error == other.error && errorUri == other.errorUri && state == other.state
    }
}
