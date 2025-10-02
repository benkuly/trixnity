package net.folivo.trixnity.vodozemac.megolm

import net.folivo.trixnity.vodozemac.*
import net.folivo.trixnity.vodozemac.bindings.megolm.GroupSessionBindings
import net.folivo.trixnity.vodozemac.utils.*
import net.folivo.trixnity.vodozemac.utils.interopScope

class GroupSession internal constructor(ptr: NativePointer) :
    Managed(ptr, GroupSessionBindings::free) {

    val sessionId: String
        get() = managedReachableScope {
            val (ptr, size) =
                withResult(NativePointerArray(2)) { GroupSessionBindings.sessionId(it, ptr) }
            ptr.toByteArray(size.intValue).decodeToString()
        }

    val messageIndex: Int
        get() = managedReachableScope { GroupSessionBindings.messageIndex(ptr) }

    val sessionConfig: MegolmSessionConfig
        get() = managedReachableScope {
            MegolmSessionConfig(GroupSessionBindings.sessionConfig(ptr))
        }

    val sessionKey: SessionKey
        get() = managedReachableScope { SessionKey(GroupSessionBindings.sessionKey(ptr)) }

    private fun <T : MegolmMessage> encryptRaw(
        plaintext: ByteArray,
        construct: (NativePointer) -> T
    ): T = managedReachableScope {
        construct(GroupSessionBindings.encrypt(ptr, plaintext.toInterop(), plaintext.size))
    }

    fun encrypt(plaintext: ByteArray): MegolmMessage.Bytes =
        encryptRaw(plaintext, MegolmMessage::Bytes)

    fun encrypt(plaintext: String): MegolmMessage.Text =
        encryptRaw(plaintext.encodeToByteArray(), MegolmMessage::Text)

    fun pickle(pickleKey: PickleKey? = null): String = managedReachableScope {
        val (ptr, size) =
            withResult(NativePointerArray(2)) {
                GroupSessionBindings.pickle(it, ptr, pickleKey.value.toInterop())
            }
        ptr.toByteArray(size.intValue).decodeToString()
    }

    companion object {
        operator fun invoke(
            sessionConfig: MegolmSessionConfig = MegolmSessionConfig.v1()
        ): GroupSession = GroupSession(GroupSessionBindings.new(sessionConfig.ptr))

        fun fromPickle(pickle: String, pickleKey: PickleKey? = null): GroupSession = interopScope {
            val pickleBytes = pickle.encodeToByteArray()

            val (tag, ptr, size) =
                withResult(NativePointerArray(3)) {
                    GroupSessionBindings.fromPickle(
                        it,
                        pickleBytes.toInterop(),
                        pickleBytes.size,
                        pickleKey.value.toInterop(),
                    )
                }

            if (tag.intValue != 0)
                throw VodozemacException(ptr.toByteArray(size.intValue).decodeToString())

            GroupSession(ptr)
        }

        fun fromLibolmPickle(pickle: String, pickleKey: String): GroupSession = interopScope {
            val pickleBytes = pickle.encodeToByteArray()
            val pickleKeyBytes = pickleKey.encodeToByteArray()

            val result =
                withResult(NativePointerArray(3)) {
                    GroupSessionBindings.fromLibolmPickle(
                        it,
                        pickleBytes.toInterop(),
                        pickleBytes.size,
                        pickleKeyBytes.toInterop(),
                        pickleKeyBytes.size)
                }

            if (result[0].intValue != 0)
                throw VodozemacException(result[1].toByteArray(result[2].intValue).decodeToString())

            GroupSession(result[1])
        }
    }
}
