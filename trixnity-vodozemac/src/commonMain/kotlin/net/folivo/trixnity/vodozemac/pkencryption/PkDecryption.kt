package net.folivo.trixnity.vodozemac.pkencryption

import net.folivo.trixnity.vodozemac.*
import net.folivo.trixnity.vodozemac.bindings.pkencryption.PkDecryptionBindings
import net.folivo.trixnity.vodozemac.utils.*
import net.folivo.trixnity.vodozemac.utils.managedReachableScope
import net.folivo.trixnity.vodozemac.utils.withResult

class PkDecryption internal constructor(ptr: NativePointer) :
    Managed(ptr, PkDecryptionBindings::free) {

    val secretKey: Curve25519SecretKey
        get() = managedReachableScope { Curve25519SecretKey(PkDecryptionBindings.secretKey(ptr)) }

    val publicKey: Curve25519PublicKey
        get() = managedReachableScope { Curve25519PublicKey(PkDecryptionBindings.publicKey(ptr)) }

    private fun <I> decryptRaw(
        message: PkEncryptionMessage,
        plaintext: (ByteArray) -> Plaintext<I>
    ): Plaintext<I> = managedReachableScope {
        val (tag, ptr, size) =
            withResult(NativePointerArray(3)) {
                PkDecryptionBindings.decrypt(
                    it,
                    ptr,
                    message.ciphertext.toInterop(),
                    message.ciphertext.size,
                    message.mac.toInterop(),
                    message.mac.size,
                    message.ephemeralKey.ptr)
            }

        val bytes = ptr.toByteArray(size.intValue)

        if (tag.intValue != 0) throw VodozemacException(bytes.decodeToString())

        plaintext(bytes)
    }

    fun decrypt(message: PkEncryptionMessage.Bytes): ByteArray =
        decryptRaw(message, Plaintext.Bytes::of).value

    fun decrypt(message: PkEncryptionMessage.Text): String =
        decryptRaw(message, Plaintext.Text::of).value

    companion object {
        operator fun invoke(): PkDecryption = PkDecryption(PkDecryptionBindings.new())

        operator fun invoke(secretKey: Curve25519SecretKey): PkDecryption =
            PkDecryption(PkDecryptionBindings.fromKey(secretKey.ptr))

        fun fromLibolmPickle(pickle: String, pickleKey: String): PkDecryption = interopScope {
            val pickleBytes = pickle.encodeToByteArray()
            val pickleKeyBytes = pickleKey.encodeToByteArray()

            val result =
                withResult(NativePointerArray(3)) {
                    PkDecryptionBindings.fromLibolmPickle(
                        it,
                        pickleBytes.toInterop(),
                        pickleBytes.size,
                        pickleKeyBytes.toInterop(),
                        pickleKeyBytes.size)
                }

            if (result[0].intValue != 0)
                throw VodozemacException(result[1].toByteArray(result[2].intValue).decodeToString())

            PkDecryption(result[1])
        }
    }
}
