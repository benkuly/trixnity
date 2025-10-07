package net.folivo.trixnity.vodozemac.olm

import net.folivo.trixnity.vodozemac.*
import net.folivo.trixnity.vodozemac.bindings.olm.SessionBindings
import net.folivo.trixnity.vodozemac.utils.*

class Session internal constructor(ptr: NativePointer) : Managed(ptr, SessionBindings::free) {

    val sessionId: String
        get() = managedReachableScope {
            val (ptr, size) =
                withResult(NativePointerArray(2)) { SessionBindings.sessionId(it, ptr) }

            ptr.toByteArray(size.intValue).decodeToString()
        }

    val hasReceivedMessage: Boolean
        get() = managedReachableScope { SessionBindings.hasReceivedMessage(ptr) }

    val sessionKeys: SessionKeys
        get() = managedReachableScope { SessionKeys(SessionBindings.sessionKeys(ptr)) }

    val sessionConfig: OlmSessionConfig
        get() = managedReachableScope { OlmSessionConfig(SessionBindings.sessionConfig(ptr)) }

    private fun <T : OlmMessage> encryptRaw(
        plaintext: ByteArray,
        normal: (OlmMessage.Message) -> T,
        preKey: (OlmMessage.Message, SessionKeys) -> T,
    ): T = managedReachableScope {
        val (kind, messagePtr, sessionKeysPtr) =
            withResult(NativePointerArray(3)) {
                SessionBindings.encrypt(it, ptr, plaintext.toInterop(), plaintext.size)
            }

        when (val msgKind = kind.intValue) {
            0 -> normal(OlmMessage.Message(messagePtr))
            1 -> preKey(OlmMessage.Message(messagePtr), SessionKeys(sessionKeysPtr))
            else -> error("unknown message kind $msgKind")
        }
    }

    fun encrypt(plaintext: ByteArray): OlmMessage.Bytes =
        encryptRaw(plaintext, OlmMessage.Normal::Bytes, OlmMessage.PreKey::Bytes)

    fun encrypt(plaintext: String): OlmMessage.Text =
        encryptRaw(plaintext.encodeToByteArray(), OlmMessage.Normal::Text, OlmMessage.PreKey::Text)

    private fun <I> decryptRaw(
        message: OlmMessage,
        plaintext: (ByteArray) -> Plaintext<I>,
    ): Plaintext<I> =
        managedReachableScope(message) {
            val result =
                withResult(NativePointerArray(3)) {
                    SessionBindings.decrypt(
                        it, ptr, message.message.ptr, message.sessionKeys?.ptr ?: nullPtr)
                }

            val bytes = result[1].toByteArray(result[2].intValue)

            if (result[0].intValue != 0) throw VodozemacException(bytes.decodeToString())

            plaintext(bytes)
        }

    fun decrypt(message: OlmMessage.Bytes): ByteArray =
        decryptRaw(message, Plaintext.Bytes::of).value

    fun decrypt(message: OlmMessage.Text): String = decryptRaw(message, Plaintext.Text::of).value

    fun pickle(pickleKey: PickleKey? = null): String = managedReachableScope {
        val (ptr, size) =
            withResult(NativePointerArray(2)) {
                SessionBindings.pickle(it, ptr, pickleKey.value.toInterop())
            }

        ptr.toByteArray(size.intValue).decodeToString()
    }

    companion object {
        fun fromPickle(pickle: String, pickleKey: PickleKey? = null): Session = interopScope {
            val pickleBytes = pickle.encodeToByteArray()

            val result =
                withResult(NativePointerArray(3)) {
                    SessionBindings.fromPickle(
                        it, pickleBytes.toInterop(), pickleBytes.size, pickleKey.value.toInterop())
                }

            if (result[0].intValue != 0)
                throw VodozemacException(result[1].toByteArray(result[2].intValue).decodeToString())

            Session(result[1])
        }

        fun fromLibolmPickle(pickle: String, pickleKey: String): Session = interopScope {
            val pickleBytes = pickle.encodeToByteArray()
            val pickleKeyBytes = pickleKey.encodeToByteArray()

            val result =
                withResult(NativePointerArray(3)) {
                    SessionBindings.fromLibolmPickle(
                        it,
                        pickleBytes.toInterop(),
                        pickleBytes.size,
                        pickleKeyBytes.toInterop(),
                        pickleKeyBytes.size)
                }

            if (result[0].intValue != 0)
                throw VodozemacException(result[1].toByteArray(result[2].intValue).decodeToString())

            Session(result[1])
        }
    }
}
