package net.folivo.trixnity.client.crypto

sealed class KeyVerifyState {
    object Valid : KeyVerifyState()
    data class Invalid(val reason: String) : KeyVerifyState()
}