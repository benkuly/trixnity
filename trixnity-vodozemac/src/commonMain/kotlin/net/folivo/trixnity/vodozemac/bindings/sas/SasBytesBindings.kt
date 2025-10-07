@file:Import("vodozemac")

package net.folivo.trixnity.vodozemac.bindings.sas

import net.folivo.trixnity.vodozemac.ExternalSymbolName
import net.folivo.trixnity.vodozemac.Import
import net.folivo.trixnity.vodozemac.InitHook
import net.folivo.trixnity.vodozemac.ModuleImport
import net.folivo.trixnity.vodozemac.utils.InteropPointer
import net.folivo.trixnity.vodozemac.utils.NativePointer

internal object SasBytesBindings {

    init {
        InitHook()
    }

    fun free(sasBytes: NativePointer) = vodozemac_sas_sas_bytes_free(sasBytes)

    fun emojiIndices(sasBytes: NativePointer, out: InteropPointer) =
        vodozemac_sas_sas_bytes_emoji_indices(sasBytes, out)

    fun decimals(sasBytes: NativePointer, out: InteropPointer) =
        vodozemac_sas_sas_bytes_decimals(sasBytes, out)

    fun asBytes(sasBytes: NativePointer, out: InteropPointer) =
        vodozemac_sas_sas_bytes_as_bytes(sasBytes, out)
}

@ModuleImport("vodozemac", "vodozemac_sas_sas_bytes_free")
@ExternalSymbolName("vodozemac_sas_sas_bytes_free")
private external fun vodozemac_sas_sas_bytes_free(sas_bytes: NativePointer)

@ModuleImport("vodozemac", "vodozemac_sas_sas_bytes_emoji_indices")
@ExternalSymbolName("vodozemac_sas_sas_bytes_emoji_indices")
private external fun vodozemac_sas_sas_bytes_emoji_indices(
    sas_bytes: NativePointer,
    emoji_indices_out: InteropPointer // must be 7 bytes
)

@ModuleImport("vodozemac", "vodozemac_sas_sas_bytes_decimals")
@ExternalSymbolName("vodozemac_sas_sas_bytes_decimals")
private external fun vodozemac_sas_sas_bytes_decimals(
    sas_bytes: NativePointer,
    decimals_out: InteropPointer // must be 3 shorts
)

@ModuleImport("vodozemac", "vodozemac_sas_sas_bytes_as_bytes")
@ExternalSymbolName("vodozemac_sas_sas_bytes_as_bytes")
private external fun vodozemac_sas_sas_bytes_as_bytes(
    sas_bytes: NativePointer,
    bytes_out: InteropPointer // must be 6 bytes
)
