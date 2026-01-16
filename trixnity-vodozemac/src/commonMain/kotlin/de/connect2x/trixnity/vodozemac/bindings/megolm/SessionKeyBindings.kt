@file:Import("vodozemac")

package de.connect2x.trixnity.vodozemac.bindings.megolm

import de.connect2x.trixnity.vodozemac.ExternalSymbolName
import de.connect2x.trixnity.vodozemac.Import
import de.connect2x.trixnity.vodozemac.InitHook
import de.connect2x.trixnity.vodozemac.ModuleImport
import de.connect2x.trixnity.vodozemac.utils.InteropPointer
import de.connect2x.trixnity.vodozemac.utils.NativePointer

internal object SessionKeyBindings {
    init {
        InitHook()
    }

    fun free(sessionKey: NativePointer) = vodozemac_megolm_session_key_free(sessionKey)

    fun toBytes(result: InteropPointer, sessionKey: NativePointer) =
        vodozemac_megolm_session_key_to_bytes(result, sessionKey)

    fun fromBytes(result: InteropPointer, bytes: InteropPointer, bytesSize: Int) =
        vodozemac_megolm_session_key_from_bytes(result, bytes, bytesSize)
}

@ModuleImport("vodozemac", "vodozemac_megolm_session_key_free")
@ExternalSymbolName("vodozemac_megolm_session_key_free")
private external fun vodozemac_megolm_session_key_free(session_key: NativePointer)

@ModuleImport("vodozemac", "vodozemac_megolm_session_key_to_bytes")
@ExternalSymbolName("vodozemac_megolm_session_key_to_bytes")
private external fun vodozemac_megolm_session_key_to_bytes(
    result: InteropPointer,
    session_key: NativePointer
)

@ModuleImport("vodozemac", "vodozemac_megolm_session_key_from_bytes")
@ExternalSymbolName("vodozemac_megolm_session_key_from_bytes")
private external fun vodozemac_megolm_session_key_from_bytes(
    result: InteropPointer,
    bytes: InteropPointer,
    bytes_size: Int
)
