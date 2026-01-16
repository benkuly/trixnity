@file:Import("vodozemac")

package de.connect2x.trixnity.vodozemac.bindings.megolm

import de.connect2x.trixnity.vodozemac.ExternalSymbolName
import de.connect2x.trixnity.vodozemac.Import
import de.connect2x.trixnity.vodozemac.InitHook
import de.connect2x.trixnity.vodozemac.ModuleImport
import de.connect2x.trixnity.vodozemac.utils.NativePointer

internal object SessionConfigBindings {

    init {
        InitHook()
    }

    fun version1(): NativePointer = vodozemac_megolm_session_config_version_1()

    fun version2(): NativePointer = vodozemac_megolm_session_config_version_2()

    fun version(sessionConfig: NativePointer): Int =
        vodozemac_megolm_session_config_version(sessionConfig)

    fun free(sessionConfig: NativePointer) = vodozemac_megolm_session_config_free(sessionConfig)
}

@ModuleImport("vodozemac", "vodozemac_megolm_session_config_version_1")
@ExternalSymbolName("vodozemac_megolm_session_config_version_1")
private external fun vodozemac_megolm_session_config_version_1(): NativePointer

@ModuleImport("vodozemac", "vodozemac_megolm_session_config_version_2")
@ExternalSymbolName("vodozemac_megolm_session_config_version_2")
private external fun vodozemac_megolm_session_config_version_2(): NativePointer

@ModuleImport("vodozemac", "vodozemac_megolm_session_config_version")
@ExternalSymbolName("vodozemac_megolm_session_config_version")
private external fun vodozemac_megolm_session_config_version(session_config: NativePointer): Int

@ModuleImport("vodozemac", "vodozemac_megolm_session_config_free")
@ExternalSymbolName("vodozemac_megolm_session_config_free")
private external fun vodozemac_megolm_session_config_free(session_config: NativePointer)
