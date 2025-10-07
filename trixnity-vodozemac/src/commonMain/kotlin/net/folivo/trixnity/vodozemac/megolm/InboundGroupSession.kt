package net.folivo.trixnity.vodozemac.megolm

import net.folivo.trixnity.vodozemac.*
import net.folivo.trixnity.vodozemac.bindings.megolm.InboundGroupSessionBindings
import net.folivo.trixnity.vodozemac.utils.*

class InboundGroupSession internal constructor(ptr: NativePointer) :
    Managed(ptr, InboundGroupSessionBindings::free) {

    val sessionId: String
        get() = managedReachableScope {
            val (ptr, size) =
                withResult(NativePointerArray(2)) { InboundGroupSessionBindings.sessionId(it, ptr) }

            ptr.toByteArray(size.intValue).decodeToString()
        }

    val firstKnownIndex: Int
        get() = managedReachableScope { InboundGroupSessionBindings.firstKnownIndex(ptr) }

    fun connected(other: InboundGroupSession): Boolean = managedReachableScope {
        InboundGroupSessionBindings.connected(ptr, other.ptr)
    }

    fun compare(other: InboundGroupSession): SessionOrdering = managedReachableScope {
        SessionOrdering.entries[InboundGroupSessionBindings.compare(ptr, other.ptr)]
    }

    fun merge(other: InboundGroupSession): InboundGroupSession? = managedReachableScope {
        InboundGroupSessionBindings.merge(ptr, other.ptr)
            .takeIf { it != nullPtr }
            ?.let(::InboundGroupSession)
    }

    fun advanceTo(index: Int): Int = managedReachableScope {
        InboundGroupSessionBindings.advanceTo(ptr, index)
    }

    private fun <I, T> decryptRaw(
        message: MegolmMessage,
        plaintext: (ByteArray) -> Plaintext<I>,
        construct: (Plaintext<I>, Int) -> T
    ): T = managedReachableScope {
        val result =
            withResult(NativePointerArray(4)) {
                InboundGroupSessionBindings.decrypt(it, ptr, message.ptr)
            }

        val plaintextOrError = result[1].toByteArray(result[2].intValue)

        if (result[0].intValue != 0) throw VodozemacException(plaintextOrError.decodeToString())

        construct(plaintext(plaintextOrError), result[3].intValue)
    }

    fun decrypt(message: MegolmMessage.Bytes): DecryptedMessage<ByteArray> =
        decryptRaw(message, Plaintext.Bytes::of, ::DecryptedMessage)

    fun decrypt(message: MegolmMessage.Text): DecryptedMessage<String> =
        decryptRaw(message, Plaintext.Text::of, ::DecryptedMessage)

    fun exportAt(index: Int): ExportedSessionKey? = managedReachableScope {
        InboundGroupSessionBindings.exportAt(ptr, index)
            .takeIf { it != nullPtr }
            ?.let(::ExportedSessionKey)
    }

    fun exportAtFirstKnownIndex(): ExportedSessionKey = managedReachableScope {
        ExportedSessionKey(InboundGroupSessionBindings.exportAtFirstKnownIndex(ptr))
    }

    fun pickle(pickleKey: PickleKey? = null): String = managedReachableScope {
        val (ptr, size) =
            withResult(NativePointerArray(2)) {
                InboundGroupSessionBindings.pickle(it, ptr, pickleKey.value.toInterop())
            }

        ptr.toByteArray(size.intValue).decodeToString()
    }

    companion object {
        operator fun invoke(
            sessionKey: SessionKey,
            sessionConfig: MegolmSessionConfig = MegolmSessionConfig.v1(),
        ): InboundGroupSession =
            InboundGroupSession(InboundGroupSessionBindings.new(sessionKey.ptr, sessionConfig.ptr))

        fun import(
            sessionKey: ExportedSessionKey,
            sessionConfig: MegolmSessionConfig = MegolmSessionConfig.v1()
        ): InboundGroupSession =
            InboundGroupSession(
                InboundGroupSessionBindings.import(sessionKey.ptr, sessionConfig.ptr))

        fun fromPickle(pickle: String, pickleKey: PickleKey? = null): InboundGroupSession {
            val pickleBytes = pickle.encodeToByteArray()

            val (tag, ptr, size) =
                withResult(NativePointerArray(3)) { result ->
                    InboundGroupSessionBindings.fromPickle(
                        result,
                        pickleBytes.toInterop(),
                        pickleBytes.size,
                        pickleKey.value.toInterop())
                }

            if (tag.intValue != 0)
                throw VodozemacException(ptr.toByteArray(size.intValue).decodeToString())

            return InboundGroupSession(ptr)
        }

        fun fromLibolmPickle(pickle: String, pickleKey: String): InboundGroupSession =
            interopScope {
                val pickleBytes = pickle.encodeToByteArray()
                val pickleKeyBytes = pickleKey.encodeToByteArray()

                val result =
                    withResult(NativePointerArray(3)) {
                        InboundGroupSessionBindings.fromLibolmPickle(
                            it,
                            pickleBytes.toInterop(),
                            pickleBytes.size,
                            pickleKeyBytes.toInterop(),
                            pickleKeyBytes.size)
                    }

                if (result[0].intValue != 0)
                    throw VodozemacException(
                        result[1].toByteArray(result[2].intValue).decodeToString())

                InboundGroupSession(result[1])
            }
    }

    enum class SessionOrdering {
        EQUAL,
        BETTER,
        WORSE,
        UNCONNECTED
    }

    class DecryptedMessage<T>(
        val plaintext: Plaintext<T>,
        val messageIndex: Int,
    ) {
        operator fun component1(): T = plaintext.value

        operator fun component2(): Int = messageIndex

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as DecryptedMessage<*>

            if (messageIndex != other.messageIndex) return false
            if (plaintext != other.plaintext) return false

            return true
        }

        override fun hashCode(): Int {
            var result = messageIndex
            result = 31 * result + plaintext.hashCode()
            return result
        }

        override fun toString(): String {
            return "DecryptedMessage(plaintext=$plaintext, messageIndex=$messageIndex)"
        }
    }
}
