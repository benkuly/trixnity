package de.connect2x.trixnity.libolm

data class OlmPkMessage(
    val cipherText: String,
    val mac: String,
    val ephemeralKey: String
)