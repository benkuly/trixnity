package de.connect2x.trixnity.libolm

actual class OlmPkEncryption private constructor() : WantsToBeFree {
    internal actual val ptr: OlmPkEncryptionPointer =
        rethrow { PkEncryption() }.unsafeCast<OlmPkEncryptionPointer>()

    actual companion object {
        actual fun create(recipientKey: String): OlmPkEncryption {
            return OlmPkEncryption().apply {
                rethrow { ptr.set_recipient_key(recipientKey) }
            }
        }
    }

    actual override fun free() = ptr.free()

    actual fun encrypt(plainText: String): OlmPkMessage {
        val message = rethrow { ptr.encrypt(plainText) }
        return OlmPkMessage(message.ciphertext, message.mac, message.ephemeral)
    }

}