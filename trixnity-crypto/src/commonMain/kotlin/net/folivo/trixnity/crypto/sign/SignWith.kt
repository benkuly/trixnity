package net.folivo.trixnity.crypto.sign

sealed interface SignWith {
    data object DeviceKey : SignWith
    data class KeyPair(val privateKey: String, val publicKey: String) : SignWith
}