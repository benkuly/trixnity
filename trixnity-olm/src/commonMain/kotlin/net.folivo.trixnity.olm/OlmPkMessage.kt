package net.folivo.trixnity.olm

data class OlmPkMessage(
    val cipherText: String,
    val mac: String,
    val ephemeralKey: String
)