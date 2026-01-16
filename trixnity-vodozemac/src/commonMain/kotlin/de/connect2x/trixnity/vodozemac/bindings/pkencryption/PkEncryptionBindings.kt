@file:Import("vodozemac")

package de.connect2x.trixnity.vodozemac.bindings.pkencryption

import de.connect2x.trixnity.vodozemac.ExternalSymbolName
import de.connect2x.trixnity.vodozemac.Import
import de.connect2x.trixnity.vodozemac.InitHook
import de.connect2x.trixnity.vodozemac.ModuleImport
import de.connect2x.trixnity.vodozemac.utils.InteropPointer
import de.connect2x.trixnity.vodozemac.utils.NativePointer

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
