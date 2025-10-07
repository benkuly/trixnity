@file:Import("vodozemac")

package net.folivo.trixnity.vodozemac.bindings.pkencryption

import net.folivo.trixnity.vodozemac.ExternalSymbolName
import net.folivo.trixnity.vodozemac.Import
import net.folivo.trixnity.vodozemac.InitHook
import net.folivo.trixnity.vodozemac.ModuleImport
import net.folivo.trixnity.vodozemac.utils.InteropPointer
import net.folivo.trixnity.vodozemac.utils.NativePointer

internal object PkEncryptionBindings {
    init {
        InitHook()
    }

    fun fromKey(key: NativePointer): NativePointer = vodozemac_pk_encryption_from_key(key)

    fun encrypt(
        result: InteropPointer,
        pk: NativePointer,
        message: InteropPointer,
        messageSize: Int
    ) = vodozemac_pk_encryption_encrypt(result, pk, message, messageSize)

    fun free(pk: NativePointer) = vodozemac_pk_encryption_free(pk)
}

@ModuleImport("vodozemac", "vodozemac_pk_encryption_from_key")
@ExternalSymbolName("vodozemac_pk_encryption_from_key")
private external fun vodozemac_pk_encryption_from_key(key: NativePointer): NativePointer

@ModuleImport("vodozemac", "vodozemac_pk_encryption_encrypt")
@ExternalSymbolName("vodozemac_pk_encryption_encrypt")
private external fun vodozemac_pk_encryption_encrypt(
    result: InteropPointer,
    pk: NativePointer,
    message: InteropPointer,
    message_size: Int
)

@ModuleImport("vodozemac", "vodozemac_pk_encryption_free")
@ExternalSymbolName("vodozemac_pk_encryption_free")
private external fun vodozemac_pk_encryption_free(pk: NativePointer)
