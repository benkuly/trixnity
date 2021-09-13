package net.folivo.trixnity.client.crypto

sealed class KeyVerificationState {
    object Valid : KeyVerificationState()
    data class Invalid(val reason: String) : KeyVerificationState()
}