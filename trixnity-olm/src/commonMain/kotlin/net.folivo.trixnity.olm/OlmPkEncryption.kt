package net.folivo.trixnity.olm

expect class OlmPkEncryption : WantsToBeFree {
    internal val ptr: OlmPkEncryptionPointer

    companion object {
        suspend fun create(recipientKey: String): OlmPkEncryption
    }

    override fun free()

    fun encrypt(plainText: String): OlmPkMessage
}