package net.folivo.trixnity.vodozemac.olm

import net.folivo.trixnity.vodozemac.Curve25519PublicKey
import net.folivo.trixnity.vodozemac.UnpaddedBase64
import net.folivo.trixnity.vodozemac.VodozemacException
import net.folivo.trixnity.vodozemac.bindings.olm.MessageBindings
import net.folivo.trixnity.vodozemac.toByteArray
import net.folivo.trixnity.vodozemac.utils.*

sealed interface OlmMessage {
    val message: Message
    val sessionKeys: SessionKeys?

    val bytes: ByteArray
        get() = interopScope {
            val (ptr, size) =
                withResult(NativePointerArray(2)) {
                    MessageBindings.toBytes(it, message.ptr, sessionKeys?.ptr ?: nullPtr)
                }
            ptr.toByteArray(size.intValue)
        }

    val base64: String
        get() = UnpaddedBase64.encode(bytes)

    sealed interface Text : OlmMessage {
        companion object
    }

    sealed interface Bytes : OlmMessage {
        companion object
    }

    sealed interface Normal : OlmMessage {
        override val message: Message
        override val sessionKeys: SessionKeys?
            get() = null

        data class Text(override val message: Message) : Normal, OlmMessage.Text {
            companion object : OlmMessageFactory<Text> by factory(::Text)
        }

        data class Bytes(override val message: Message) : Normal, OlmMessage.Bytes {
            companion object : OlmMessageFactory<Bytes> by factory(::Bytes)
        }
    }

    sealed interface PreKey : OlmMessage {
        override val message: Message
        override val sessionKeys: SessionKeys

        data class Text(
            override val message: Message,
            override val sessionKeys: SessionKeys,
        ) : PreKey, OlmMessage.Text {
            companion object : OlmMessageFactory<Text> by factory(::Text)
        }

        data class Bytes(
            override val message: Message,
            override val sessionKeys: SessionKeys,
        ) : PreKey, OlmMessage.Bytes {
            companion object : OlmMessageFactory<Bytes> by factory(::Bytes)
        }
    }

    companion object {
        private fun <T : PreKey> factory(constructor: (Message, SessionKeys) -> T) =
            factory(0) { message, keys -> constructor(Message(message), SessionKeys(keys)) }

        private fun <T : Normal> factory(constructor: (Message) -> T) =
            factory(1) { message, _ -> constructor(Message(message)) }

        private fun <T : OlmMessage> factory(
            messageType: Int,
            constructor: (NativePointer, NativePointer) -> T
        ) =
            object : OlmMessageFactory<T> {
                override fun invoke(bytes: ByteArray): T = interopScope {
                    val result =
                        withResult(NativePointerArray(4)) {
                            MessageBindings.fromBytes(
                                it, messageType, bytes.toInterop(), bytes.size)
                        }

                    if (result[0].intValue != 0)
                        throw VodozemacException(
                            result[1].toByteArray(result[2].intValue).decodeToString())

                    // TODO: swap enum order in rust
                    if (result[1].intValue != messageType xor 1)
                        error("expected message of type $messageType, was ${result[1].intValue}")

                    constructor(result[2], result[3])
                }
            }
    }

    interface OlmMessageFactory<T : OlmMessage> {
        operator fun invoke(bytes: ByteArray): T

        operator fun invoke(base64: String): T = this(UnpaddedBase64.decode(base64))
    }

    class Message internal constructor(ptr: NativePointer) : Managed(ptr, MessageBindings::free) {

        val ratchetKey: Curve25519PublicKey
            get() = managedReachableScope { Curve25519PublicKey(MessageBindings.ratchetKey(ptr)) }

        val chainIndex: Long
            get() = managedReachableScope { MessageBindings.chainIndex(ptr) }

        val ciphertext: ByteArray
            get() = managedReachableScope {
                val (ptr, size) =
                    withResult(NativePointerArray(2)) { MessageBindings.ciphertext(it, ptr) }

                ptr.toByteArray(size.intValue)
            }

        val version: Int
            get() = managedReachableScope { MessageBindings.version(ptr) }

        val macTruncated: Boolean
            get() = managedReachableScope { MessageBindings.macTruncated(ptr) }
    }
}
