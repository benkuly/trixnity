@file:Import("vodozemac")

package de.connect2x.trixnity.vodozemac.bindings.megolm

import de.connect2x.trixnity.vodozemac.ExternalSymbolName
import de.connect2x.trixnity.vodozemac.Import
import de.connect2x.trixnity.vodozemac.InitHook
import de.connect2x.trixnity.vodozemac.ModuleImport
import de.connect2x.trixnity.vodozemac.utils.InteropPointer
import de.connect2x.trixnity.vodozemac.utils.NativePointer

internal object InboundGroupSessionBindings {

    init {
        InitHook()
    }

    fun free(inboundGroupSession: NativePointer) =
        vodozemac_megolm_inbound_group_session_free(inboundGroupSession)

    fun new(key: NativePointer, sessionConfig: NativePointer): NativePointer =
        vodozemac_megolm_inbound_group_session_new(key, sessionConfig)

    fun import(sessionKey: NativePointer, sessionConfig: NativePointer): NativePointer =
        vodozemac_megolm_inbound_group_session_import(sessionKey, sessionConfig)

    fun sessionId(result: InteropPointer, inboundGroupSession: NativePointer) =
        vodozemac_megolm_inbound_group_session_session_id(result, inboundGroupSession)

    fun connected(inboundGroupSession: NativePointer, other: NativePointer): Boolean =
        vodozemac_megolm_inbound_group_session_connected(inboundGroupSession, other) == 1

    fun compare(inboundGroupSession: NativePointer, other: NativePointer): Int =
        vodozemac_megolm_inbound_group_session_compare(inboundGroupSession, other)

    fun merge(inboundGroupSession: NativePointer, other: NativePointer): NativePointer =
        vodozemac_megolm_inbound_group_session_merge(inboundGroupSession, other)

    fun firstKnownIndex(inboundGroupSession: NativePointer): Int =
        vodozemac_megolm_inbound_group_session_first_known_index(inboundGroupSession)

    fun advanceTo(inboundGroupSession: NativePointer, index: Int): Int =
        vodozemac_megolm_inbound_group_session_advance_to(inboundGroupSession, index)

    fun decrypt(
        result: InteropPointer,
        inboundGroupSession: NativePointer,
        message: NativePointer
    ) = vodozemac_megolm_inbound_group_session_decrypt(result, inboundGroupSession, message)

    fun exportAt(inboundGroupSession: NativePointer, index: Int): NativePointer =
        vodozemac_megolm_inbound_group_session_export_at(inboundGroupSession, index)

    fun exportAtFirstKnownIndex(inboundGroupSession: NativePointer): NativePointer =
        vodozemac_megolm_inbound_group_session_export_at_first_known_index(inboundGroupSession)

    fun pickle(
        result: InteropPointer,
        inboundGroupSession: NativePointer,
        pickleKey: InteropPointer?
    ) = vodozemac_megolm_inbound_group_session_pickle(result, inboundGroupSession, pickleKey)

    fun fromPickle(
        result: InteropPointer,
        ciphertext: InteropPointer,
        ciphertextSize: Int,
        pickleKey: InteropPointer?
    ) =
        vodozemac_megolm_inbound_group_session_from_pickle(
            result, ciphertext, ciphertextSize, pickleKey)

    fun fromLibolmPickle(
        result: InteropPointer,
        pickle: InteropPointer,
        pickleSize: Int,
        pickleKey: InteropPointer,
        pickleKeySize: Int,
    ) =
        vodozemac_megolm_inbound_group_session_from_libolm_pickle(
            result, pickle, pickleSize, pickleKey, pickleKeySize)
}

@ModuleImport("vodozemac", "vodozemac_megolm_inbound_group_session_new")
@ExternalSymbolName("vodozemac_megolm_inbound_group_session_new")
private external fun vodozemac_megolm_inbound_group_session_new(
    key: NativePointer,
    session_config: NativePointer
): NativePointer

@ModuleImport("vodozemac", "vodozemac_megolm_inbound_group_session_free")
@ExternalSymbolName("vodozemac_megolm_inbound_group_session_free")
private external fun vodozemac_megolm_inbound_group_session_free(
    inbound_group_session: NativePointer,
)

@ModuleImport("vodozemac", "vodozemac_megolm_inbound_group_session_import")
@ExternalSymbolName("vodozemac_megolm_inbound_group_session_import")
private external fun vodozemac_megolm_inbound_group_session_import(
    session_key: NativePointer,
    session_config: NativePointer,
): NativePointer

@ModuleImport("vodozemac", "vodozemac_megolm_inbound_group_session_session_id")
@ExternalSymbolName("vodozemac_megolm_inbound_group_session_session_id")
private external fun vodozemac_megolm_inbound_group_session_session_id(
    result: InteropPointer,
    inbound_group_session: NativePointer,
)

@ModuleImport("vodozemac", "vodozemac_megolm_inbound_group_session_connected")
@ExternalSymbolName("vodozemac_megolm_inbound_group_session_connected")
private external fun vodozemac_megolm_inbound_group_session_connected(
    inbound_group_session: NativePointer,
    other: NativePointer,
): Int

@ModuleImport("vodozemac", "vodozemac_megolm_inbound_group_session_compare")
@ExternalSymbolName("vodozemac_megolm_inbound_group_session_compare")
private external fun vodozemac_megolm_inbound_group_session_compare(
    inbound_group_session: NativePointer,
    other: NativePointer
): Int

@ModuleImport("vodozemac", "vodozemac_megolm_inbound_group_session_merge")
@ExternalSymbolName("vodozemac_megolm_inbound_group_session_merge")
private external fun vodozemac_megolm_inbound_group_session_merge(
    inbound_group_session: NativePointer,
    other: NativePointer,
): NativePointer

@ModuleImport("vodozemac", "vodozemac_megolm_inbound_group_session_first_known_index")
@ExternalSymbolName("vodozemac_megolm_inbound_group_session_first_known_index")
private external fun vodozemac_megolm_inbound_group_session_first_known_index(
    inbound_group_session: NativePointer,
): Int

@ModuleImport("vodozemac", "vodozemac_megolm_inbound_group_session_advance_to")
@ExternalSymbolName("vodozemac_megolm_inbound_group_session_advance_to")
private external fun vodozemac_megolm_inbound_group_session_advance_to(
    inbound_group_session: NativePointer,
    index: Int,
): Int

@ModuleImport("vodozemac", "vodozemac_megolm_inbound_group_session_decrypt")
@ExternalSymbolName("vodozemac_megolm_inbound_group_session_decrypt")
private external fun vodozemac_megolm_inbound_group_session_decrypt(
    result: InteropPointer,
    inbound_group_session: NativePointer,
    message: NativePointer,
)

@ModuleImport("vodozemac", "vodozemac_megolm_inbound_group_session_export_at")
@ExternalSymbolName("vodozemac_megolm_inbound_group_session_export_at")
private external fun vodozemac_megolm_inbound_group_session_export_at(
    inbound_group_session: NativePointer,
    index: Int,
): NativePointer

@ModuleImport("vodozemac", "vodozemac_megolm_inbound_group_session_export_at_first_known_index")
@ExternalSymbolName("vodozemac_megolm_inbound_group_session_export_at_first_known_index")
private external fun vodozemac_megolm_inbound_group_session_export_at_first_known_index(
    inbound_group_session: NativePointer,
): NativePointer

@ModuleImport("vodozemac", "vodozemac_megolm_inbound_group_session_pickle")
@ExternalSymbolName("vodozemac_megolm_inbound_group_session_pickle")
private external fun vodozemac_megolm_inbound_group_session_pickle(
    result: InteropPointer,
    inbound_group_session: NativePointer,
    pickle_key: InteropPointer?,
)

@ModuleImport("vodozemac", "vodozemac_megolm_inbound_group_session_from_pickle")
@ExternalSymbolName("vodozemac_megolm_inbound_group_session_from_pickle")
private external fun vodozemac_megolm_inbound_group_session_from_pickle(
    result: InteropPointer,
    ciphertext: InteropPointer,
    ciphertext_size: Int,
    pickle_key: InteropPointer?,
)

@ModuleImport("vodozemac", "vodozemac_megolm_inbound_group_session_from_libolm_pickle")
@ExternalSymbolName("vodozemac_megolm_inbound_group_session_from_libolm_pickle")
private external fun vodozemac_megolm_inbound_group_session_from_libolm_pickle(
    result: InteropPointer,
    pickle: InteropPointer,
    pickle_size: Int,
    pickle_key: InteropPointer,
    pickle_key_size: Int,
)
