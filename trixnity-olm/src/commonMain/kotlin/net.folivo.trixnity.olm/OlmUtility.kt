package net.folivo.trixnity.olm

expect class OlmUtility : WantsToBeFree {
    internal val ptr: OlmUtilityPointer

    companion object {
        fun create(): OlmUtility
    }

    override fun free()

    fun sha256(input: ByteArray): String
    fun sha256(input: String): String

    fun verifyEd25519(key: String, message: String, signature: String)
}