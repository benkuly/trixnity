package net.folivo.trixnity.vodozemac

import kotlin.jvm.JvmInline
import net.folivo.trixnity.vodozemac.bindings.*
import net.folivo.trixnity.vodozemac.bindings.Curve25519PublicKeyBindings
import net.folivo.trixnity.vodozemac.bindings.Ed25519PublicKeyBindings
import net.folivo.trixnity.vodozemac.bindings.Ed25519SecretKeyBindings
import net.folivo.trixnity.vodozemac.bindings.Ed25519SignatureBindings
import net.folivo.trixnity.vodozemac.utils.*
import net.folivo.trixnity.vodozemac.utils.interopScope
import net.folivo.trixnity.vodozemac.utils.managedReachableScope

class Ed25519PublicKey internal constructor(ptr: NativePointer) :
    Managed(ptr, Ed25519PublicKeyBindings::free) {

    val bytes: ByteArray
        get() = managedReachableScope {
            withResult(ByteArray(32)) { Ed25519PublicKeyBindings.toBytes(ptr, it) }
        }

    val base64: String
        get() = UnpaddedBase64.encode(bytes)

    fun verify(message: ByteArray, signature: Ed25519Signature) = managedReachableScope {
        val result =
            withResult(NativePointerArray(3)) {
                Ed25519PublicKeyBindings.verify(
                    it, ptr, message.toInterop(), message.size, signature.ptr)
            }

        if (result[0].intValue != 0)
            throw VodozemacException(result[1].toByteArray(result[2].intValue).decodeToString())
    }

    fun verify(message: String, signature: Ed25519Signature) =
        verify(message.encodeToByteArray(), signature)

    companion object {
        operator fun invoke(bytes: ByteArray): Ed25519PublicKey = interopScope {
            require(bytes.size == 32) { "invalid key size: ${bytes.size}" }

            val ptr = Ed25519PublicKeyBindings.fromBytes(bytes.toInterop())
            if (ptr == nullPtr) throw VodozemacException("Invalid Ed25519PublicKey")

            Ed25519PublicKey(ptr)
        }

        operator fun invoke(base64: String): Ed25519PublicKey = this(UnpaddedBase64.decode(base64))
    }
}

class Ed25519SecretKey internal constructor(ptr: NativePointer) :
    Managed(ptr, Ed25519SecretKeyBindings::free) {

    val bytes: ByteArray
        get() = managedReachableScope {
            withResult(ByteArray(32)) { Ed25519SecretKeyBindings.toBytes(ptr, it) }
        }

    val base64: String
        get() = UnpaddedBase64.encode(bytes)

    val publicKey: Ed25519PublicKey
        get() = managedReachableScope { Ed25519PublicKey(Ed25519SecretKeyBindings.publicKey(ptr)) }

    fun sign(message: ByteArray): Ed25519Signature = managedReachableScope {
        Ed25519Signature(Ed25519SecretKeyBindings.sign(ptr, message.toInterop(), message.size))
    }

    fun sign(message: String): Ed25519Signature = sign(message.encodeToByteArray())

    companion object {
        operator fun invoke(): Ed25519SecretKey = Ed25519SecretKey(Ed25519SecretKeyBindings.new())

        operator fun invoke(bytes: ByteArray): Ed25519SecretKey = interopScope {
            require(bytes.size == 32) { "invalid key size: ${bytes.size}" }
            Ed25519SecretKey(Ed25519SecretKeyBindings.fromBytes(bytes.toInterop()))
        }

        operator fun invoke(base64: String): Ed25519SecretKey = this(UnpaddedBase64.decode(base64))
    }
}

class Curve25519PublicKey internal constructor(ptr: NativePointer) :
    Managed(ptr, Curve25519PublicKeyBindings::free) {

    val bytes: ByteArray
        get() = managedReachableScope {
            val result = ByteArray(32)
            val resultPtr = result.toInteropForResult()
            Curve25519PublicKeyBindings.toBytes(ptr, resultPtr)
            result.fromInterop(resultPtr)
            result
        }

    val base64: String
        get() = UnpaddedBase64.encode(bytes)

    companion object {
        operator fun invoke(bytes: ByteArray): Curve25519PublicKey = interopScope {
            require(bytes.size == 32) { "invalid key size: ${bytes.size}" }
            Curve25519PublicKey(Curve25519PublicKeyBindings.fromBytes(bytes.toInterop()))
        }

        operator fun invoke(base64: String): Curve25519PublicKey =
            this(UnpaddedBase64.decode(base64))
    }
}

class Curve25519SecretKey internal constructor(ptr: NativePointer) :
    Managed(ptr, Curve25519SecretKeyBindings::free) {

    val bytes: ByteArray
        get() = managedReachableScope {
            withResult(ByteArray(32)) { Curve25519SecretKeyBindings.toBytes(ptr, it) }
        }

    val base64: String
        get() = UnpaddedBase64.encode(bytes)

    val publicKey: Curve25519PublicKey
        get() = managedReachableScope {
            Curve25519PublicKey(Curve25519SecretKeyBindings.publicKey(ptr))
        }

    fun diffieHellman(theirPublicKey: Curve25519PublicKey) = managedReachableScope {
        var wasContributory: Boolean
        val secret =
            withResult(ByteArray(32)) {
                wasContributory =
                    Curve25519SecretKeyBindings.diffieHellman(ptr, theirPublicKey.ptr, it)
            }

        SharedSecret(secret = secret, wasContributory = wasContributory)
    }

    class SharedSecret(val secret: ByteArray, val wasContributory: Boolean) {
        operator fun component1() = secret

        operator fun component2() = wasContributory
    }

    companion object {
        operator fun invoke(): Curve25519SecretKey =
            Curve25519SecretKey(Curve25519SecretKeyBindings.new())

        operator fun invoke(bytes: ByteArray): Curve25519SecretKey = interopScope {
            require(bytes.size == 32) { "invalid key size: ${bytes.size}" }
            Curve25519SecretKey(Curve25519SecretKeyBindings.fromBytes(bytes.toInterop()))
        }

        operator fun invoke(base64: String): Curve25519SecretKey =
            this(UnpaddedBase64.decode(base64))
    }
}

class Ed25519Signature internal constructor(ptr: NativePointer) :
    Managed(ptr, Ed25519SignatureBindings::free) {

    val bytes: ByteArray
        get() = managedReachableScope {
            val result = ByteArray(64)
            val resultPtr = result.toInteropForResult()
            Ed25519SignatureBindings.toBytes(ptr, resultPtr)
            result.fromInterop(resultPtr)
            result
        }

    val base64: String
        get() = UnpaddedBase64.encode(bytes)

    companion object {

        operator fun invoke(bytes: ByteArray): Ed25519Signature = interopScope {
            require(bytes.size == 64) { "invalid signature size: ${bytes.size}" }
            Ed25519Signature(Ed25519SignatureBindings.fromBytes(bytes.toInterop()))
        }

        operator fun invoke(base64: String): Ed25519Signature = this(UnpaddedBase64.decode(base64))
    }
}

@JvmInline
value class PickleKey internal constructor(val value: ByteArray) {
    init {
        require(value.size == 32) { "invalid key size: ${value.size}" }
    }

    val base64: String
        get() = UnpaddedBase64.encode(value)

    companion object {
        operator fun invoke(bytes: ByteArray): PickleKey = PickleKey(bytes)

        operator fun invoke(bytes: ByteArray?): PickleKey? = bytes?.let(PickleKey::invoke)

        operator fun invoke(base64: String): PickleKey = PickleKey(UnpaddedBase64.decode(base64))

        operator fun invoke(base64: String?): PickleKey? = base64?.let(PickleKey::invoke)
    }
}

val PickleKey?.value: Nothing?
    get() = null

fun Nothing?.toInterop(): InteropPointer? = null
