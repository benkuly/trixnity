package net.folivo.trixnity.crypto.jose

import io.ktor.http.Url
import kotlinx.serialization.SerialName

data class Claims<T>(
    @SerialName("sub") val subject: String,
    @SerialName("iat") val issuedAt: Long?,
    @SerialName("iss") val issuer: Url?,
    val additional: T
)