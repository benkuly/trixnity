@file:Import("vodozemac")

package net.folivo.trixnity.vodozemac.bindings.olm

import net.folivo.trixnity.vodozemac.ExternalSymbolName
import net.folivo.trixnity.vodozemac.Import
import net.folivo.trixnity.vodozemac.InitHook
import net.folivo.trixnity.vodozemac.ModuleImport
import net.folivo.trixnity.vodozemac.utils.InteropPointer
import net.folivo.trixnity.vodozemac.utils.NativePointer

internal object MessageBindings {

    init {
        InitHook()
    }

    fun free(message: NativePointer) = vodozemac_olm_message_free(message)

    fun ratchetKey(message: NativePointer): NativePointer =
        vodozemac_olm_message_ratchet_key(message)

    fun chainIndex(message: NativePointer): Long = vodozemac_olm_message_chain_index(message)

    fun ciphertext(result: InteropPointer, message: NativePointer) =
        vodozemac_olm_message_ciphertext(result, message)

    fun version(message: NativePointer): Int = vodozemac_olm_message_version(message)

    fun macTruncated(message: NativePointer): Boolean =
        vodozemac_olm_message_mac_truncated(message) == 1

    fun toBytes(result: InteropPointer, message: NativePointer, sessionKeys: NativePointer) =
        vodozemac_olm_message_to_bytes(result, message, sessionKeys)

    fun fromBytes(
        result: InteropPointer,
        messageType: Int,
        bytes: InteropPointer,
        bytes_size: Int
    ) = vodozemac_olm_message_from_bytes(result, messageType, bytes, bytes_size)
}

@ModuleImport("vodozemac", "vodozemac_olm_message_free")
@ExternalSymbolName("vodozemac_olm_message_free")
private external fun vodozemac_olm_message_free(message: NativePointer)

@ModuleImport("vodozemac", "vodozemac_olm_message_ratchet_key")
@ExternalSymbolName("vodozemac_olm_message_ratchet_key")
private external fun vodozemac_olm_message_ratchet_key(message: NativePointer): NativePointer

@ModuleImport("vodozemac", "vodozemac_olm_message_chain_index")
@ExternalSymbolName("vodozemac_olm_message_chain_index")
private external fun vodozemac_olm_message_chain_index(message: NativePointer): Long

@ModuleImport("vodozemac", "vodozemac_olm_message_ciphertext")
@ExternalSymbolName("vodozemac_olm_message_ciphertext")
private external fun vodozemac_olm_message_ciphertext(
    result: InteropPointer,
    message: NativePointer
)

@ModuleImport("vodozemac", "vodozemac_olm_message_version")
@ExternalSymbolName("vodozemac_olm_message_version")
private external fun vodozemac_olm_message_version(message: NativePointer): Int

@ModuleImport("vodozemac", "vodozemac_olm_message_mac_truncated")
@ExternalSymbolName("vodozemac_olm_message_mac_truncated")
private external fun vodozemac_olm_message_mac_truncated(message: NativePointer): Int

@ModuleImport("vodozemac", "vodozemac_olm_message_to_bytes")
@ExternalSymbolName("vodozemac_olm_message_to_bytes")
private external fun vodozemac_olm_message_to_bytes(
    result: InteropPointer,
    message: NativePointer,
    session_keys: NativePointer
)

@ModuleImport("vodozemac", "vodozemac_olm_message_from_bytes")
@ExternalSymbolName("vodozemac_olm_message_from_bytes")
private external fun vodozemac_olm_message_from_bytes(
    result: InteropPointer,
    message_type: Int,
    bytes: InteropPointer,
    bytes_size: Int,
)
