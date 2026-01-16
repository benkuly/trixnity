package de.connect2x.trixnity.vodozemac.pkencryption

import de.connect2x.trixnity.vodozemac.Curve25519PublicKey
import de.connect2x.trixnity.vodozemac.bindings.pkencryption.PkEncryptionBindings
import de.connect2x.trixnity.vodozemac.toByteArray
import de.connect2x.trixnity.vodozemac.utils.*

class PkEncryption internal constructor(ptr: NativePointer) :
    Managed(ptr, PkEncryptionBindings::free) {

    private fun <T : PkEncryptionMessage> encryptRaw(
        bytes: ByteArray,
        construct: (ByteArray, ByteArray, Curve25519PublicKey) -> T
    ): T = managedReachableScope {
        val result =
            withResult(NativePointerArray(5)) {
                PkEncryptionBindings.encrypt(it, ptr, bytes.toInterop(), bytes.size)
            }
        val ciphertext = result[0].toByteArray(result[1].intValue)
        val mac = result[2].toByteArray(result[3].intValue)
        val ephemeralKey = Curve25519PublicKey(result[4])

        construct(ciphertext, mac, ephemeralKey)
    }

    fun encrypt(plaintext: ByteArray): PkEncryptionMessage.Bytes =
        encryptRaw(plaintext, PkEncryptionMessage::Bytes)

    fun encrypt(plaintext: String): PkEncryptionMessage.Text =
        encryptRaw(plaintext.encodeToByteArray(), PkEncryptionMessage::Text)

    companion object {
        operator fun invoke(publicKey: Curve25519PublicKey): PkEncryption =
            PkEncryption(PkEncryptionBindings.fromKey(publicKey.ptr))
    }
}
