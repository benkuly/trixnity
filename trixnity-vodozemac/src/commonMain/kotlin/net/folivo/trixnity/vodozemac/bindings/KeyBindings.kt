@file:Import("vodozemac")

package net.folivo.trixnity.vodozemac.bindings

import net.folivo.trixnity.vodozemac.ExternalSymbolName
import net.folivo.trixnity.vodozemac.Import
import net.folivo.trixnity.vodozemac.InitHook
import net.folivo.trixnity.vodozemac.ModuleImport
import net.folivo.trixnity.vodozemac.utils.InteropPointer
import net.folivo.trixnity.vodozemac.utils.NativePointer

internal object Ed25519PublicKeyBindings {

    init {
        InitHook()
    }

    fun fromBytes(bytes: InteropPointer): NativePointer =
        vodozemac_ed25519_public_key_from_bytes(bytes)

    fun toBytes(key: NativePointer, out: InteropPointer) =
        vodozemac_ed25519_public_key_to_bytes(key, out)

    fun verify(
        result: InteropPointer,
        key: NativePointer,
        message: InteropPointer,
        messageSize: Int,
        signature: NativePointer
    ) = vodozemac_ed25519_public_key_verify(result, key, message, messageSize, signature)

    fun free(key: NativePointer) = vodozemac_ed25519_public_key_free(key)
}

internal object Ed25519SecretKeyBindings {

    init {
        InitHook()
    }

    fun new(): NativePointer = vodozemac_ed25519_secret_key_new()

    fun fromBytes(bytes: InteropPointer): NativePointer =
        vodozemac_ed25519_secret_key_from_bytes(bytes)

    fun toBytes(key: NativePointer, out: InteropPointer) =
        vodozemac_ed25519_secret_key_to_bytes(key, out)

    fun sign(
        key: NativePointer,
        message: InteropPointer,
        messageSize: Int,
    ): NativePointer = vodozemac_ed25519_secret_key_sign(key, message, messageSize)

    fun publicKey(
        key: NativePointer,
    ): NativePointer = vodozemac_ed25519_secret_key_public_key(key)

    fun free(key: NativePointer) = vodozemac_ed25519_secret_key_free(key)
}

internal object Curve25519PublicKeyBindings {

    init {
        InitHook()
    }

    fun fromBytes(bytes: InteropPointer): NativePointer =
        vodozemac_curve25519_public_key_from_bytes(bytes)

    fun toBytes(key: NativePointer, out: InteropPointer) =
        vodozemac_curve25519_public_key_to_bytes(key, out)

    fun free(key: NativePointer) = vodozemac_curve25519_public_key_free(key)
}

internal object Curve25519SecretKeyBindings {

    init {
        InitHook()
    }

    fun new(): NativePointer = vodozemac_curve25519_secret_key_new()

    fun fromBytes(bytes: InteropPointer): NativePointer =
        vodozemac_curve25519_secret_key_from_bytes(bytes)

    fun toBytes(key: NativePointer, bytes: InteropPointer) =
        vodozemac_curve25519_secret_key_to_bytes(key, bytes)

    fun diffieHellman(
        key: NativePointer,
        theirPublicKey: NativePointer,
        secret: InteropPointer
    ): Boolean = vodozemac_curve25519_secret_key_diffie_hellman(key, theirPublicKey, secret) == 1

    fun publicKey(key: NativePointer): NativePointer =
        vodozemac_curve25519_secret_key_public_key(key)

    fun free(key: NativePointer) = vodozemac_curve25519_secret_key_free(key)
}

internal object Ed25519SignatureBindings {

    init {
        InitHook()
    }

    fun fromBytes(bytes: InteropPointer): NativePointer =
        vodozemac_ed25519_signature_from_bytes(bytes)

    fun toBytes(signature: NativePointer, out: InteropPointer) =
        vodozemac_ed25519_signature_to_bytes(signature, out)

    fun free(signature: NativePointer) = vodozemac_ed25519_signature_free(signature)
}

@ModuleImport("vodozemac", "vodozemac_ed25519_public_key_from_bytes")
@ExternalSymbolName("vodozemac_ed25519_public_key_from_bytes")
private external fun vodozemac_ed25519_public_key_from_bytes(
    bytes: InteropPointer // must be 32 bytes
): NativePointer

@ModuleImport("vodozemac", "vodozemac_ed25519_public_key_to_bytes")
@ExternalSymbolName("vodozemac_ed25519_public_key_to_bytes")
private external fun vodozemac_ed25519_public_key_to_bytes(
    key: NativePointer,
    bytes: InteropPointer // must be 32 bytes
)

@ModuleImport("vodozemac", "vodozemac_ed25519_public_key_verify")
@ExternalSymbolName("vodozemac_ed25519_public_key_verify")
private external fun vodozemac_ed25519_public_key_verify(
    result: InteropPointer,
    key: NativePointer,
    message: InteropPointer,
    message_size: Int,
    signature: NativePointer
)

@ModuleImport("vodozemac", "vodozemac_ed25519_public_key_free")
@ExternalSymbolName("vodozemac_ed25519_public_key_free")
private external fun vodozemac_ed25519_public_key_free(key: NativePointer)

@ModuleImport("vodozemac", "vodozemac_curve25519_public_key_from_bytes")
@ExternalSymbolName("vodozemac_curve25519_public_key_from_bytes")
private external fun vodozemac_curve25519_public_key_from_bytes(
    bytes: InteropPointer // must be 32 bytes
): NativePointer

@ModuleImport("vodozemac", "vodozemac_curve25519_public_key_to_bytes")
@ExternalSymbolName("vodozemac_curve25519_public_key_to_bytes")
private external fun vodozemac_curve25519_public_key_to_bytes(
    key: NativePointer,
    bytes: InteropPointer // must be 32 bytes
)

@ModuleImport("vodozemac", "vodozemac_curve25519_public_key_free")
@ExternalSymbolName("vodozemac_curve25519_public_key_free")
private external fun vodozemac_curve25519_public_key_free(key: NativePointer)

@ModuleImport("vodozemac", "vodozemac_ed25519_secret_key_new")
@ExternalSymbolName("vodozemac_ed25519_secret_key_new")
private external fun vodozemac_ed25519_secret_key_new(): NativePointer

@ModuleImport("vodozemac", "vodozemac_ed25519_secret_key_from_bytes")
@ExternalSymbolName("vodozemac_ed25519_secret_key_from_bytes")
private external fun vodozemac_ed25519_secret_key_from_bytes(bytes: InteropPointer): NativePointer

@ModuleImport("vodozemac", "vodozemac_ed25519_secret_key_to_bytes")
@ExternalSymbolName("vodozemac_ed25519_secret_key_to_bytes")
private external fun vodozemac_ed25519_secret_key_to_bytes(key: NativePointer, out: InteropPointer)

@ModuleImport("vodozemac", "vodozemac_ed25519_secret_key_sign")
@ExternalSymbolName("vodozemac_ed25519_secret_key_sign")
private external fun vodozemac_ed25519_secret_key_sign(
    key: NativePointer,
    message: InteropPointer,
    messageSize: Int
): NativePointer

@ModuleImport("vodozemac", "vodozemac_ed25519_secret_key_public_key")
@ExternalSymbolName("vodozemac_ed25519_secret_key_public_key")
private external fun vodozemac_ed25519_secret_key_public_key(key: NativePointer): NativePointer

@ModuleImport("vodozemac", "vodozemac_ed25519_secret_key_free")
@ExternalSymbolName("vodozemac_ed25519_secret_key_free")
private external fun vodozemac_ed25519_secret_key_free(key: NativePointer)

@ModuleImport("vodozemac", "vodozemac_curve25519_secret_key_new")
@ExternalSymbolName("vodozemac_curve25519_secret_key_new")
private external fun vodozemac_curve25519_secret_key_new(): NativePointer

@ModuleImport("vodozemac", "vodozemac_curve25519_secret_key_from_bytes")
@ExternalSymbolName("vodozemac_curve25519_secret_key_from_bytes")
private external fun vodozemac_curve25519_secret_key_from_bytes(
    bytes: InteropPointer
): NativePointer

@ModuleImport("vodozemac", "vodozemac_curve25519_secret_key_to_bytes")
@ExternalSymbolName("vodozemac_curve25519_secret_key_to_bytes")
private external fun vodozemac_curve25519_secret_key_to_bytes(
    key: NativePointer,
    bytes: InteropPointer
)

@ModuleImport("vodozemac", "vodozemac_curve25519_secret_key_diffie_hellman")
@ExternalSymbolName("vodozemac_curve25519_secret_key_diffie_hellman")
private external fun vodozemac_curve25519_secret_key_diffie_hellman(
    key: NativePointer,
    their_public_key: NativePointer,
    secret: InteropPointer
): Int

@ModuleImport("vodozemac", "vodozemac_curve25519_secret_key_public_key")
@ExternalSymbolName("vodozemac_curve25519_secret_key_public_key")
private external fun vodozemac_curve25519_secret_key_public_key(key: NativePointer): NativePointer

@ModuleImport("vodozemac", "vodozemac_curve25519_secret_key_free")
@ExternalSymbolName("vodozemac_curve25519_secret_key_free")
private external fun vodozemac_curve25519_secret_key_free(key: NativePointer)

@ModuleImport("vodozemac", "vodozemac_ed25519_signature_from_bytes")
@ExternalSymbolName("vodozemac_ed25519_signature_from_bytes")
private external fun vodozemac_ed25519_signature_from_bytes(
    bytes: InteropPointer // must be 64 bytes
): NativePointer

@ModuleImport("vodozemac", "vodozemac_ed25519_signature_to_bytes")
@ExternalSymbolName("vodozemac_ed25519_signature_to_bytes")
private external fun vodozemac_ed25519_signature_to_bytes(
    signature: NativePointer,
    bytes: InteropPointer // must be 64 bytes
)

@ModuleImport("vodozemac", "vodozemac_ed25519_signature_free")
@ExternalSymbolName("vodozemac_ed25519_signature_free")
private external fun vodozemac_ed25519_signature_free(signature: NativePointer)
