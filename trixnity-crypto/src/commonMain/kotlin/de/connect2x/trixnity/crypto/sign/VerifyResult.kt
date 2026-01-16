package de.connect2x.trixnity.crypto.sign

sealed interface VerifyResult {
    data object Valid : VerifyResult
    data class MissingSignature(val reason: String) : VerifyResult
    data class Invalid(val reason: String) : VerifyResult
}