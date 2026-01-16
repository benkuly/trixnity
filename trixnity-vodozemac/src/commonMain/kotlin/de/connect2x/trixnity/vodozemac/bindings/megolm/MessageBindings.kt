@file:Import("vodozemac")

package de.connect2x.trixnity.vodozemac.bindings.megolm

import de.connect2x.trixnity.vodozemac.ExternalSymbolName
import de.connect2x.trixnity.vodozemac.Import
import de.connect2x.trixnity.vodozemac.InitHook
import de.connect2x.trixnity.vodozemac.ModuleImport
import de.connect2x.trixnity.vodozemac.utils.InteropPointer
import de.connect2x.trixnity.vodozemac.utils.NativePointer

internal object MessageBindings {

    init {
        InitHook()
    }

    fun free(message: NativePointer) = vodozemac_megolm_message_free(message)

    fun ciphertext(result: InteropPointer, message: NativePointer) =
        vodozemac_megolm_message_ciphertext(result, message)

    fun index(message: NativePointer): Int = vodozemac_megolm_message_index(message)

    fun mac(result: InteropPointer, message: NativePointer) =
        vodozemac_megolm_message_mac(result, message)

    fun signature(message: NativePointer): NativePointer =
        vodozemac_megolm_message_signature(message)

    fun toBytes(result: InteropPointer, message: NativePointer) =
        vodozemac_megolm_message_to_bytes(result, message)

    fun fromBytes(result: InteropPointer, bytes: InteropPointer, bytes_size: Int) =
        vodozemac_megolm_message_from_bytes(result, bytes, bytes_size)
}

@ModuleImport("vodozemac", "vodozemac_megolm_message_free")
@ExternalSymbolName("vodozemac_megolm_message_free")
private external fun vodozemac_megolm_message_free(message: NativePointer)

@ModuleImport("vodozemac", "vodozemac_megolm_message_ciphertext")
@ExternalSymbolName("vodozemac_megolm_message_ciphertext")
private external fun vodozemac_megolm_message_ciphertext(
    result: InteropPointer,
    message: NativePointer
)

@ModuleImport("vodozemac", "vodozemac_megolm_message_index")
@ExternalSymbolName("vodozemac_megolm_message_index")
private external fun vodozemac_megolm_message_index(message: NativePointer): Int

@ModuleImport("vodozemac", "vodozemac_megolm_message_mac")
@ExternalSymbolName("vodozemac_megolm_message_mac")
private external fun vodozemac_megolm_message_mac(
    result: InteropPointer,
    message: NativePointer,
)

@ModuleImport("vodozemac", "vodozemac_megolm_message_signature")
@ExternalSymbolName("vodozemac_megolm_message_signature")
private external fun vodozemac_megolm_message_signature(message: NativePointer): NativePointer

@ModuleImport("vodozemac", "vodozemac_megolm_message_to_bytes")
@ExternalSymbolName("vodozemac_megolm_message_to_bytes")
private external fun vodozemac_megolm_message_to_bytes(
    result: InteropPointer,
    message: NativePointer,
)

@ModuleImport("vodozemac", "vodozemac_megolm_message_from_bytes")
@ExternalSymbolName("vodozemac_megolm_message_from_bytes")
private external fun vodozemac_megolm_message_from_bytes(
    result: InteropPointer,
    bytes: InteropPointer,
    bytes_size: Int,
)
