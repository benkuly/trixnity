package de.connect2x.trixnity.libolm

data class OlmVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
)

expect fun getOlmVersion(): OlmVersion