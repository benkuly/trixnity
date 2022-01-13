package net.folivo.trixnity.client.crypto

sealed interface VerifyResult {
    object Valid : VerifyResult
    data class MissingSignature(val reason: String) : VerifyResult
    data class Invalid(val reason: String) : VerifyResult
}