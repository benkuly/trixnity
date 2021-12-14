package net.folivo.trixnity.client.crypto

sealed interface VerifyResult {
    object Valid : VerifyResult
    object MissingSignature : VerifyResult
    data class Invalid(val reason: String) : VerifyResult
}