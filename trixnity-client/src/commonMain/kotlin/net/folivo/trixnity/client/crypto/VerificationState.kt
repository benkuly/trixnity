package net.folivo.trixnity.client.crypto

sealed interface VerificationState {
    object Verified : VerificationState
    object Valid : VerificationState
    data class Invalid(val reason: String) : VerificationState
}