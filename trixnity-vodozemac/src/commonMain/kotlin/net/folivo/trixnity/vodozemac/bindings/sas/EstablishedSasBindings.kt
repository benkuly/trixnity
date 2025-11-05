@file:Import("vodozemac")

package net.folivo.trixnity.vodozemac.bindings.sas

import net.folivo.trixnity.vodozemac.ExternalSymbolName
import net.folivo.trixnity.vodozemac.Import
import net.folivo.trixnity.vodozemac.InitHook
import net.folivo.trixnity.vodozemac.ModuleImport
import net.folivo.trixnity.vodozemac.utils.InteropPointer
import net.folivo.trixnity.vodozemac.utils.NativePointer

internal object EstablishedSasBindings {

    init {
        InitHook()
    }

    fun free(sas: NativePointer) = vodozemac_sas_established_sas_free(sas)

    fun bytes(sas: NativePointer, info: InteropPointer, info_size: Int): NativePointer =
        vodozemac_sas_established_sas_bytes(sas, info, info_size)

    fun calculateMac(
        sas: NativePointer,
        input: InteropPointer,
        input_size: Int,
        info: InteropPointer,
        info_size: Int
    ): NativePointer =
        vodozemac_sas_established_sas_calculate_mac(sas, input, input_size, info, info_size)

    fun calculateMacInvalidBase64(
        result: InteropPointer,
        sas: NativePointer,
        input: InteropPointer,
        input_size: Int,
        info: InteropPointer,
        info_size: Int
    ) =
        vodozemac_sas_established_sas_calculate_mac_invalid_base64(
            result, sas, input, input_size, info, info_size)

    fun verifyMac(
        sas: NativePointer,
        input: InteropPointer,
        input_size: Int,
        info: InteropPointer,
        info_size: Int,
        tag: NativePointer
    ): Boolean =
        vodozemac_sas_established_sas_verify_mac(sas, input, input_size, info, info_size, tag) == 1

    fun ourPublicKey(sas: NativePointer): NativePointer =
        vodozemac_sas_established_sas_our_public_key(sas)

    fun theirPublicKey(sas: NativePointer): NativePointer =
        vodozemac_sas_established_sas_their_public_key(sas)
}

@ModuleImport("vodozemac", "vodozemac_sas_established_sas_free")
@ExternalSymbolName("vodozemac_sas_established_sas_free")
private external fun vodozemac_sas_established_sas_free(sas: NativePointer)

@ModuleImport("vodozemac", "vodozemac_sas_established_sas_bytes")
@ExternalSymbolName("vodozemac_sas_established_sas_bytes")
private external fun vodozemac_sas_established_sas_bytes(
    sas: NativePointer,
    info: InteropPointer,
    info_size: Int
): NativePointer

@ModuleImport("vodozemac", "vodozemac_sas_established_sas_calculate_mac")
@ExternalSymbolName("vodozemac_sas_established_sas_calculate_mac")
private external fun vodozemac_sas_established_sas_calculate_mac(
    sas: NativePointer,
    input: InteropPointer,
    input_size: Int,
    info: InteropPointer,
    info_size: Int
): NativePointer

@ModuleImport("vodozemac", "vodozemac_sas_established_sas_calculate_mac_invalid_base64")
@ExternalSymbolName("vodozemac_sas_established_sas_calculate_mac_invalid_base64")
private external fun vodozemac_sas_established_sas_calculate_mac_invalid_base64(
    result: InteropPointer,
    sas: NativePointer,
    input: InteropPointer,
    input_size: Int,
    info: InteropPointer,
    info_size: Int
)

@ModuleImport("vodozemac", "vodozemac_sas_established_sas_verify_mac")
@ExternalSymbolName("vodozemac_sas_established_sas_verify_mac")
private external fun vodozemac_sas_established_sas_verify_mac(
    sas: NativePointer,
    input: InteropPointer,
    input_size: Int,
    info: InteropPointer,
    info_size: Int,
    tag: NativePointer
): Int

@ModuleImport("vodozemac", "vodozemac_sas_established_sas_our_public_key")
@ExternalSymbolName("vodozemac_sas_established_sas_our_public_key")
private external fun vodozemac_sas_established_sas_our_public_key(sas: NativePointer): NativePointer

@ModuleImport("vodozemac", "vodozemac_sas_established_sas_their_public_key")
@ExternalSymbolName("vodozemac_sas_established_sas_their_public_key")
private external fun vodozemac_sas_established_sas_their_public_key(
    sas: NativePointer
): NativePointer
