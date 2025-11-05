package net.folivo.trixnity.vodozemac.megolm

import net.folivo.trixnity.vodozemac.*
import net.folivo.trixnity.vodozemac.UnpaddedBase64
import net.folivo.trixnity.vodozemac.bindings.megolm.MessageBindings
import net.folivo.trixnity.vodozemac.utils.*
import net.folivo.trixnity.vodozemac.utils.managedReachableScope
import net.folivo.trixnity.vodozemac.utils.withResult

sealed class MegolmMessage(ptr: NativePointer) : Managed(ptr, MessageBindings::free) {

    interface MegolmMessageFactory<T : MegolmMessage> {
        operator fun invoke(bytes: ByteArray): T

        operator fun invoke(base64: String): T = this(UnpaddedBase64.decode(base64))
    }

    class Bytes(ptr: NativePointer) : MegolmMessage(ptr) {
        companion object : MegolmMessageFactory<Bytes> by factory(::Bytes)
    }

    class Text(ptr: NativePointer) : MegolmMessage(ptr) {
        companion object : MegolmMessageFactory<Text> by factory(::Text)
    }

    val ciphertext: ByteArray
        get() = managedReachableScope {
            val (ptr, size) =
                withResult(NativePointerArray(2)) { MessageBindings.ciphertext(it, ptr) }
            ptr.toByteArray(size.intValue)
        }

    val index: Int
        get() = managedReachableScope { MessageBindings.index(ptr) }

    val mac: ByteArray
        get() = managedReachableScope {
            val (ptr, size) = withResult(NativePointerArray(2)) { MessageBindings.mac(it, ptr) }
            ptr.toByteArray(size.intValue)
        }

    val signature: Ed25519Signature
        get() = managedReachableScope { Ed25519Signature(MessageBindings.signature(ptr)) }

    val bytes: ByteArray
        get() = managedReachableScope {
            val (ptr, size) = withResult(NativePointerArray(2)) { MessageBindings.toBytes(it, ptr) }
            ptr.toByteArray(size.intValue)
        }

    val base64: String
        get() = UnpaddedBase64.encode(bytes)

    companion object {
        private fun <T : MegolmMessage> factory(constructor: (NativePointer) -> T) =
            object : MegolmMessageFactory<T> {
                override operator fun invoke(bytes: ByteArray): T = interopScope {
                    val (tag, messageOrErrPtr, errSize) =
                        withResult(NativePointerArray(3)) {
                            MessageBindings.fromBytes(it, bytes.toInterop(), bytes.size)
                        }
                    if (tag.intValue != 0)
                        throw VodozemacException(
                            messageOrErrPtr.toByteArray(errSize.intValue).decodeToString())

                    constructor(messageOrErrPtr)
                }
            }
    }
}
