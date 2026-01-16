@file:Import("vodozemac")

package de.connect2x.trixnity.vodozemac.bindings.sas

import de.connect2x.trixnity.vodozemac.ExternalSymbolName
import de.connect2x.trixnity.vodozemac.Import
import de.connect2x.trixnity.vodozemac.InitHook
import de.connect2x.trixnity.vodozemac.ModuleImport
import de.connect2x.trixnity.vodozemac.utils.InteropPointer
import de.connect2x.trixnity.vodozemac.utils.NativePointer

internal object MacBindings {

    init {
        InitHook()
    }

    fun free(mac: NativePointer) = vodozemac_sas_mac_free(mac)

    fun asBytes(mac: NativePointer, out: InteropPointer) = vodozemac_sas_mac_as_bytes(mac, out)

    fun fromSlice(bytes: InteropPointer): NativePointer = vodozemac_sas_mac_from_slice(bytes)
}

@ModuleImport("vodozemac", "vodozemac_sas_mac_free")
@ExternalSymbolName("vodozemac_sas_mac_free")
private external fun vodozemac_sas_mac_free(mac: NativePointer)

@ModuleImport("vodozemac", "vodozemac_sas_mac_as_bytes")
@ExternalSymbolName("vodozemac_sas_mac_as_bytes")
private external fun vodozemac_sas_mac_as_bytes(
    mac: NativePointer,
    bytes_out: InteropPointer // must be 32 bytes
)

@ModuleImport("vodozemac", "vodozemac_sas_mac_from_slice")
@ExternalSymbolName("vodozemac_sas_mac_from_slice")
private external fun vodozemac_sas_mac_from_slice(
    bytes: InteropPointer // must be 32 bytes
): NativePointer
