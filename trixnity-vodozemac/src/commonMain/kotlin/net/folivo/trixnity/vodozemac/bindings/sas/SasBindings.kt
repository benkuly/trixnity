@file:Import("vodozemac")

package net.folivo.trixnity.vodozemac.bindings.sas

import net.folivo.trixnity.vodozemac.ExternalSymbolName
import net.folivo.trixnity.vodozemac.Import
import net.folivo.trixnity.vodozemac.InitHook
import net.folivo.trixnity.vodozemac.ModuleImport
import net.folivo.trixnity.vodozemac.utils.NativePointer

internal object SasBindings {

    init {
        InitHook()
    }

    fun new(): NativePointer = vodozemac_sas_sas_new()

    fun publicKey(sas: NativePointer): NativePointer = vodozemac_sas_sas_public_key(sas)

    fun diffieHellman(sas: NativePointer, theirPublicKey: NativePointer): NativePointer =
        vodozemac_sas_sas_diffie_hellman(sas, theirPublicKey)

    fun free(sas: NativePointer) = vodozemac_sas_sas_free(sas)
}

@ModuleImport("vodozemac", "vodozemac_sas_sas_new")
@ExternalSymbolName("vodozemac_sas_sas_new")
private external fun vodozemac_sas_sas_new(): NativePointer

@ModuleImport("vodozemac", "vodozemac_sas_sas_public_key")
@ExternalSymbolName("vodozemac_sas_sas_public_key")
private external fun vodozemac_sas_sas_public_key(sas: NativePointer): NativePointer

@ModuleImport("vodozemac", "vodozemac_sas_sas_diffie_hellman")
@ExternalSymbolName("vodozemac_sas_sas_diffie_hellman")
private external fun vodozemac_sas_sas_diffie_hellman(
    sas: NativePointer,
    their_public_key: NativePointer
): NativePointer

@ModuleImport("vodozemac", "vodozemac_sas_sas_free")
@ExternalSymbolName("vodozemac_sas_sas_free")
private external fun vodozemac_sas_sas_free(sas: NativePointer)
