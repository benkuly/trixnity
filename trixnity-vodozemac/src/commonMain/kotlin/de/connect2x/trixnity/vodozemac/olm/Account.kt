package de.connect2x.trixnity.vodozemac.olm

import de.connect2x.trixnity.vodozemac.*
import de.connect2x.trixnity.vodozemac.bindings.olm.AccountBindings
import de.connect2x.trixnity.vodozemac.utils.*

class Account internal constructor(ptr: NativePointer) : Managed(ptr, AccountBindings::free) {

    val identityKeys: IdentityKeys
        get() = managedReachableScope {
            val (ed25519, curve25519) =
                withResult(NativePointerArray(2)) { AccountBindings.identityKeys(it, ptr) }

            IdentityKeys(
                Ed25519PublicKey(ed25519),
                Curve25519PublicKey(curve25519),
            )
        }

    val ed25519Key: Ed25519PublicKey
        get() = managedReachableScope { Ed25519PublicKey(AccountBindings.ed25519Key(ptr)) }

    val curve25519Key: Curve25519PublicKey
        get() = managedReachableScope { Curve25519PublicKey(AccountBindings.curve25519Key(ptr)) }

    val maxNumberOfOneTimeKeys: Int
        get() = managedReachableScope { AccountBindings.maxNumberOfOneTimeKeys(ptr) }

    val storedOneTimeKeyCount: Int
        get() = managedReachableScope { AccountBindings.storedOneTimeKeyCount(ptr) }

    val oneTimeKeys: Map<String, Curve25519PublicKey>
        get() = managedReachableScope {
            val (ptr, size) =
                withResult(NativePointerArray(2)) { AccountBindings.oneTimeKeys(it, ptr) }

            if (size.intValue % 2 != 0) error("size must be divisible by 2, was: ${size.intValue}")

            ptr.toNativePointerArray(size.intValue)
                .asPtrSequence()
                .chunked(2)
                .map { it[0] to it[1] }
                .map { (keyIdPtr, keyPtr) ->
                    keyIdPtr.toByteArray(11).decodeToString() to Curve25519PublicKey(keyPtr)
                }
                .toMap()
        }

    val fallbackKey: Pair<String, Curve25519PublicKey>?
        get() = managedReachableScope {
            val (ptr, size) =
                withResult(NativePointerArray(2)) { AccountBindings.fallbackKey(it, ptr) }

            if (size.intValue != 0 && size.intValue != 2)
                error("size must be 0 or 2, was: ${size.intValue}")

            ptr.toNativePointerArray(size.intValue)
                .asPtrSequence()
                .chunked(2)
                .map { it[0] to it[1] }
                .map { (keyIdPtr, keyPtr) ->
                    keyIdPtr.toByteArray(11).decodeToString() to Curve25519PublicKey(keyPtr)
                }
                .firstOrNull()
        }

    fun sign(message: ByteArray): Ed25519Signature = managedReachableScope {
        Ed25519Signature(AccountBindings.sign(ptr, message.toInterop(), message.size))
    }

    fun sign(message: String): Ed25519Signature = sign(message.encodeToByteArray())

    fun createOutboundSession(
        identityKey: Curve25519PublicKey,
        oneTimeKey: Curve25519PublicKey,
        sessionConfig: OlmSessionConfig = OlmSessionConfig.v1(),
    ): Session =
        managedReachableScope(sessionConfig, identityKey, oneTimeKey) {
            Session(
                AccountBindings.createOutboundSession(
                    ptr, sessionConfig.ptr, identityKey.ptr, oneTimeKey.ptr))
        }

    private fun <I, T> createInboundSessionRaw(
        preKeyMessage: OlmMessage.PreKey,
        theirIdentityKey: Curve25519PublicKey,
        plaintext: (ByteArray) -> Plaintext<I>,
        construct: (Plaintext<I>, Session) -> T
    ): T =
        managedReachableScope(theirIdentityKey, preKeyMessage) {
            val result =
                withResult(NativePointerArray(4)) {
                    AccountBindings.createInboundSession(
                        it,
                        ptr,
                        theirIdentityKey.ptr,
                        preKeyMessage.message.ptr,
                        preKeyMessage.sessionKeys.ptr)
                }

            if (result[0].intValue != 0)
                throw VodozemacException(result[1].toByteArray(result[2].intValue).decodeToString())

            construct(plaintext(result[1].toByteArray(result[2].intValue)), Session(result[3]))
        }

    fun createInboundSession(
        preKeyMessage: OlmMessage.PreKey.Bytes,
        theirIdentityKey: Curve25519PublicKey = preKeyMessage.sessionKeys.identityKey,
    ): InboundSessionCreationResult<ByteArray> =
        createInboundSessionRaw(
            preKeyMessage = preKeyMessage,
            theirIdentityKey = theirIdentityKey,
            plaintext = Plaintext.Bytes::of,
            construct = ::InboundSessionCreationResult)

    fun createInboundSession(
        preKeyMessage: OlmMessage.PreKey.Text,
        theirIdentityKey: Curve25519PublicKey = preKeyMessage.sessionKeys.identityKey,
    ): InboundSessionCreationResult<String> =
        createInboundSessionRaw(
            preKeyMessage = preKeyMessage,
            theirIdentityKey = theirIdentityKey,
            plaintext = Plaintext.Text::of,
            construct = ::InboundSessionCreationResult)

    fun generateOneTimeKeys(
        count: Int,
    ): OneTimeKeyGenerationResult = managedReachableScope {
        require(count > 0) { "count must be > 0, was: $count" }

        val result =
            withResult(NativePointerArray(4)) {
                AccountBindings.generateOneTimeKeys(it, ptr, count)
            }

        val created = result[0].toNativePointerArray(result[1].intValue)
        val removed = result[2].toNativePointerArray(result[3].intValue)

        OneTimeKeyGenerationResult(
            created = created.map(::Curve25519PublicKey),
            removed = removed.map(::Curve25519PublicKey),
        )
    }

    fun generateFallbackKey(): Curve25519PublicKey? = managedReachableScope {
        AccountBindings.generateFallbackKey(ptr)
            .takeIf { it != nullPtr }
            ?.let(::Curve25519PublicKey)
    }

    fun forgetFallbackKey(): Boolean = managedReachableScope {
        AccountBindings.forgetFallbackKey(ptr)
    }

    fun markKeysAsPublished(): Unit = managedReachableScope {
        AccountBindings.markKeysSsPublished(ptr)
    }

    fun pickle(pickleKey: PickleKey? = null): String = managedReachableScope {
        val (ptr, size) =
            withResult(NativePointerArray(2)) {
                AccountBindings.pickle(it, ptr, pickleKey.value.toInterop())
            }

        ptr.toByteArray(size.intValue).decodeToString()
    }

    fun dehydratedDevice(pickleKey: PickleKey): DehydratedDevice = managedReachableScope {
        val result =
            withResult(NativePointerArray(5)) {
                AccountBindings.toDehydratedDevice(it, ptr, pickleKey.value.toInterop())
            }

        if (result[0].intValue != 0)
            throw VodozemacException(result[1].toByteArray(result[2].intValue).decodeToString())

        val ciphertext = result[1].toByteArray(result[2].intValue).decodeToString()
        val nonce = result[3].toByteArray(result[4].intValue).decodeToString()

        DehydratedDevice(
            ciphertext = ciphertext,
            nonce = nonce,
        )
    }

    companion object {

        operator fun invoke(): Account = Account(AccountBindings.new())

        fun fromPickle(pickle: String, pickleKey: PickleKey? = null): Account = interopScope {
            val pickleBytes = pickle.encodeToByteArray()

            val result =
                withResult(NativePointerArray(3)) {
                    AccountBindings.fromPickle(
                        it, pickleBytes.toInterop(), pickleBytes.size, pickleKey.value.toInterop())
                }

            if (result[0].intValue != 0)
                throw VodozemacException(result[1].toByteArray(result[2].intValue).decodeToString())

            Account(result[1])
        }

        fun fromDehydratedDevice(ciphertext: String, nonce: String, pickleKey: PickleKey): Account =
            interopScope {
                val ciphertextBytes = ciphertext.encodeToByteArray()
                val nonceBytes = nonce.encodeToByteArray()

                val result =
                    withResult(NativePointerArray(3)) {
                        AccountBindings.fromDehydratedDevice(
                            it,
                            ciphertextBytes.toInterop(),
                            ciphertextBytes.size,
                            nonceBytes.toInterop(),
                            nonceBytes.size,
                            pickleKey.value.toInterop())
                    }

                if (result[0].intValue != 0)
                    throw VodozemacException(
                        result[1].toByteArray(result[2].intValue).decodeToString())

                Account(result[1])
            }

        fun fromLibolmPickle(pickle: String, pickleKey: String): Account = interopScope {
            val pickleBytes = pickle.encodeToByteArray()
            val pickleKeyBytes = pickleKey.encodeToByteArray()

            val result =
                withResult(NativePointerArray(3)) {
                    AccountBindings.fromLibolmPickle(
                        it,
                        pickleBytes.toInterop(),
                        pickleBytes.size,
                        pickleKeyBytes.toInterop(),
                        pickleKeyBytes.size)
                }

            if (result[0].intValue != 0)
                throw VodozemacException(result[1].toByteArray(result[2].intValue).decodeToString())

            Account(result[1])
        }
    }

    data class OneTimeKeyGenerationResult(
        val created: List<Curve25519PublicKey>,
        val removed: List<Curve25519PublicKey>,
    )

    class InboundSessionCreationResult<I>(
        val plaintext: Plaintext<I>,
        val session: Session,
    ) {
        operator fun component1(): I = plaintext.value

        operator fun component2(): Session = session

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as InboundSessionCreationResult<*>

            if (plaintext != other.plaintext) return false
            if (session != other.session) return false

            return true
        }

        override fun hashCode(): Int {
            var result = plaintext.hashCode()
            result = 31 * result + session.hashCode()
            return result
        }

        override fun toString(): String {
            return "InboundSessionCreationResult(plaintext=$plaintext, session=$session)"
        }
    }

    data class DehydratedDevice(val ciphertext: String, val nonce: String)

    data class IdentityKeys(
        val ed25519: Ed25519PublicKey,
        val curve25519: Curve25519PublicKey,
    ) : AutoCloseable {
        override fun close() {
            ed25519.close()
            curve25519.close()
        }
    }
}
