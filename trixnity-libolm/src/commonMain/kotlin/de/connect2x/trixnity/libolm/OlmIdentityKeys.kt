package de.connect2x.trixnity.libolm

import kotlinx.serialization.Serializable

@Serializable
data class OlmIdentityKeys(
    val curve25519: String,
    val ed25519: String
)
