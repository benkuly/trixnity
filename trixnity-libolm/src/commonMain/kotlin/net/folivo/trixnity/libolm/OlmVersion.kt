package net.folivo.trixnity.libolm

data class OlmVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
)

expect fun getOlmVersion(): OlmVersion