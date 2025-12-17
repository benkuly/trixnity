package net.folivo.trixnity.libolm

import kotlinx.serialization.Serializable

@Serializable
data class OlmOneTimeKeys(
    val curve25519: Map<String, String>
)
