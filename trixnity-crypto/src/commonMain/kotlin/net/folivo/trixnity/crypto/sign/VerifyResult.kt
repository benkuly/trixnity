package net.folivo.trixnity.crypto.sign

sealed interface VerifyResult {
    object Valid : VerifyResult
    data class MissingSignature(val reason: String) : VerifyResult
    data class Invalid(val reason: String) : VerifyResult
}