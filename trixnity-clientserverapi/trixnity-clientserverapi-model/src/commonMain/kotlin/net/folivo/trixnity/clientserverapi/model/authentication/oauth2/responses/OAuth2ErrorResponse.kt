package net.folivo.trixnity.clientserverapi.model.authentication.oauth2.responses

import kotlinx.serialization.Serializable

@Serializable
data class OAuth2ErrorResponse(val error: String, val description: String? = null)