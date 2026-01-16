@file:Import("vodozemac")

package de.connect2x.trixnity.vodozemac.bindings.olm

import de.connect2x.trixnity.vodozemac.ExternalSymbolName
import de.connect2x.trixnity.vodozemac.Import
import de.connect2x.trixnity.vodozemac.InitHook
import de.connect2x.trixnity.vodozemac.ModuleImport
import de.connect2x.trixnity.vodozemac.utils.InteropPointer
import de.connect2x.trixnity.vodozemac.utils.NativePointer

internal object SessionKeysBindings {

    init {
        InitHook()
    }

    fun free(sessionKeys: NativePointer) = vodozemac_olm_session_keys_free(sessionKeys)

    fun identityKey(sessionKeys: NativePointer) =
        vodozemac_olm_session_keys_identity_key(sessionKeys)

    fun baseKey(sessionKeys: NativePointer) = vodozemac_olm_session_keys_base_key(sessionKeys)

    fun oneTimeKey(sessionKeys: NativePointer) =
        vodozemac_olm_session_keys_one_time_key(sessionKeys)

    fun sessionId(result: InteropPointer, sessionKeys: NativePointer) =
        vodozemac_olm_session_keys_session_id(result, sessionKeys)
}

@ModuleImport("vodozemac", "vodozemac_olm_session_keys_free")
@ExternalSymbolName("vodozemac_olm_session_keys_free")
private external fun vodozemac_olm_session_keys_free(session_keys: NativePointer)

@ModuleImport("vodozemac", "vodozemac_olm_session_keys_identity_key")
@ExternalSymbolName("vodozemac_olm_session_keys_identity_key")
private external fun vodozemac_olm_session_keys_identity_key(
    session_keys: NativePointer
): NativePointer

@ModuleImport("vodozemac", "vodozemac_olm_session_keys_base_key")
@ExternalSymbolName("vodozemac_olm_session_keys_base_key")
private external fun vodozemac_olm_session_keys_base_key(session_keys: NativePointer): NativePointer

@ModuleImport("vodozemac", "vodozemac_olm_session_keys_one_time_key")
@ExternalSymbolName("vodozemac_olm_session_keys_one_time_key")
private external fun vodozemac_olm_session_keys_one_time_key(
    session_keys: NativePointer
): NativePointer

@ModuleImport("vodozemac", "vodozemac_olm_session_keys_session_id")
@ExternalSymbolName("vodozemac_olm_session_keys_session_id")
private external fun vodozemac_olm_session_keys_session_id(
    result: InteropPointer,
    session_keys: NativePointer
)
