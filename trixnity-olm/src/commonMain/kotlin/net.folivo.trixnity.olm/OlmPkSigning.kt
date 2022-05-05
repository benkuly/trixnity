package net.folivo.trixnity.olm

expect class OlmPkSigning : WantsToBeFree {
    internal val ptr: OlmPkSigningPointer

    companion object {
        suspend fun create(privateKey: String? = null): OlmPkSigning
    }

    val privateKey: String
    val publicKey: String

    override fun free()

    fun sign(message: String): String
}