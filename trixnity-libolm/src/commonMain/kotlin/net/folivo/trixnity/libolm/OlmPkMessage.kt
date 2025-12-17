package net.folivo.trixnity.libolm

data class OlmPkMessage(
    val cipherText: String,
    val mac: String,
    val ephemeralKey: String
)