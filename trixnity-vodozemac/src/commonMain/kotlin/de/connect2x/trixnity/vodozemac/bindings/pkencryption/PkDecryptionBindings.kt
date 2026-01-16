@file:Import("vodozemac")

package de.connect2x.trixnity.vodozemac.bindings.pkencryption

import de.connect2x.trixnity.vodozemac.ExternalSymbolName
import de.connect2x.trixnity.vodozemac.Import
import de.connect2x.trixnity.vodozemac.InitHook
import de.connect2x.trixnity.vodozemac.ModuleImport
import de.connect2x.trixnity.vodozemac.utils.InteropPointer
import de.connect2x.trixnity.vodozemac.utils.NativePointer

internal object PkDecryptionBindings {
    init {
        InitHook()
    }

    fun new(): NativePointer = vodozemac_pk_decryption_new()

    fun free(pk: NativePointer) = vodozemac_pk_decryption_free(pk)

    fun fromKey(key: NativePointer): NativePointer = vodozemac_pk_decryption_from_key(key)

    fun secretKey(pk: NativePointer): NativePointer = vodozemac_pk_decryption_secret_key(pk)

    fun publicKey(pk: NativePointer): NativePointer = vodozemac_pk_decryption_public_key(pk)

    fun decrypt(
        result: InteropPointer,
        pk: NativePointer,
        ciphertext: InteropPointer,
        ciphertext_size: Int,
        mac: InteropPointer,
        mac_size: Int,
        ephemeral_key: NativePointer
    ) =
        vodozemac_pk_decryption_decrypt(
            result, pk, ciphertext, ciphertext_size, mac, mac_size, ephemeral_key)

    fun fromLibolmPickle(
        result: InteropPointer,
        pickle: InteropPointer,
        pickleSize: Int,
        pickleKey: InteropPointer,
        pickleKeySize: Int,
    ) =
        vodozemac_pk_decryption_from_libolm_pickle(
            result, pickle, pickleSize, pickleKey, pickleKeySize)
}

@ModuleImport("vodozemac", "vodozemac_pk_decryption_new")
@ExternalSymbolName("vodozemac_pk_decryption_new")
private external fun vodozemac_pk_decryption_new(): NativePointer

@ModuleImport("vodozemac", "vodozemac_pk_decryption_from_key")
@ExternalSymbolName("vodozemac_pk_decryption_from_key")
private external fun vodozemac_pk_decryption_from_key(key: NativePointer): NativePointer

@ModuleImport("vodozemac", "vodozemac_pk_decryption_secret_key")
@ExternalSymbolName("vodozemac_pk_decryption_secret_key")
private external fun vodozemac_pk_decryption_secret_key(pk: NativePointer): NativePointer

@ModuleImport("vodozemac", "vodozemac_pk_decryption_public_key")
@ExternalSymbolName("vodozemac_pk_decryption_public_key")
private external fun vodozemac_pk_decryption_public_key(pk: NativePointer): NativePointer

@ModuleImport("vodozemac", "vodozemac_pk_decryption_decrypt")
@ExternalSymbolName("vodozemac_pk_decryption_decrypt")
private external fun vodozemac_pk_decryption_decrypt(
    result: InteropPointer,
    pk: NativePointer,
    ciphertext: InteropPointer,
    ciphertext_size: Int,
    mac: InteropPointer,
    mac_size: Int,
    ephemeral_key: NativePointer
)

@ModuleImport("vodozemac", "vodozemac_pk_decryption_free")
@ExternalSymbolName("vodozemac_pk_decryption_free")
private external fun vodozemac_pk_decryption_free(pk: NativePointer)

@ModuleImport("vodozemac", "vodozemac_pk_decryption_from_libolm_pickle")
@ExternalSymbolName("vodozemac_pk_decryption_from_libolm_pickle")
private external fun vodozemac_pk_decryption_from_libolm_pickle(
    result: InteropPointer,
    pickle: InteropPointer,
    pickle_size: Int,
    pickle_key: InteropPointer,
    pickle_key_size: Int,
)
