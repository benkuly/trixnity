package net.folivo.trixnity.olm

data class OlmVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
)

expect suspend fun getOlmVersion(): OlmVersion