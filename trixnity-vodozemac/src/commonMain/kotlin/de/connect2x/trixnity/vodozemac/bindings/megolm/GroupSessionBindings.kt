@file:Import("vodozemac")

package de.connect2x.trixnity.vodozemac.bindings.megolm

import de.connect2x.trixnity.vodozemac.ExternalSymbolName
import de.connect2x.trixnity.vodozemac.Import
import de.connect2x.trixnity.vodozemac.InitHook
import de.connect2x.trixnity.vodozemac.ModuleImport
import de.connect2x.trixnity.vodozemac.utils.InteropPointer
import de.connect2x.trixnity.vodozemac.utils.NativePointer

internal object GroupSessionBindings {

    init {
        InitHook()
    }

    fun new(config: NativePointer): NativePointer = vodozemac_megolm_group_session_new(config)

    fun free(groupSession: NativePointer) = vodozemac_megolm_group_session_free(groupSession)

    fun sessionId(result: InteropPointer, groupSession: NativePointer) =
        vodozemac_megolm_group_session_session_id(result, groupSession)

    fun messageIndex(groupSession: NativePointer): Int =
        vodozemac_megolm_group_session_message_index(groupSession)

    fun sessionConfig(groupSession: NativePointer): NativePointer =
        vodozemac_megolm_group_session_session_config(groupSession)

    fun encrypt(
        groupSession: NativePointer,
        plaintext: InteropPointer,
        plaintextSize: Int
    ): NativePointer =
        vodozemac_megolm_group_session_encrypt(groupSession, plaintext, plaintextSize)

    fun sessionKey(groupSession: NativePointer): NativePointer =
        vodozemac_megolm_group_session_session_key(groupSession)

    fun pickle(result: InteropPointer, groupSession: NativePointer, pickleKey: InteropPointer?) =
        vodozemac_megolm_group_session_pickle(result, groupSession, pickleKey)

    fun fromPickle(
        result: InteropPointer,
        ciphertext: InteropPointer,
        ciphertextSize: Int,
        pickleKey: InteropPointer?
    ) = vodozemac_megolm_group_session_from_pickle(result, ciphertext, ciphertextSize, pickleKey)

    fun fromLibolmPickle(
        result: InteropPointer,
        pickle: InteropPointer,
        pickleSize: Int,
        pickleKey: InteropPointer,
        pickleKeySize: Int,
    ) =
        vodozemac_megolm_group_session_from_libolm_pickle(
            result, pickle, pickleSize, pickleKey, pickleKeySize)
}

@ModuleImport("vodozemac", "vodozemac_megolm_group_session_new")
@ExternalSymbolName("vodozemac_megolm_group_session_new")
private external fun vodozemac_megolm_group_session_new(config: NativePointer): NativePointer

@ModuleImport("vodozemac", "vodozemac_megolm_group_session_free")
@ExternalSymbolName("vodozemac_megolm_group_session_free")
private external fun vodozemac_megolm_group_session_free(group_session: NativePointer)

@ModuleImport("vodozemac", "vodozemac_megolm_group_session_session_id")
@ExternalSymbolName("vodozemac_megolm_group_session_session_id")
private external fun vodozemac_megolm_group_session_session_id(
    result: InteropPointer,
    group_session: NativePointer
)

@ModuleImport("vodozemac", "vodozemac_megolm_group_session_message_index")
@ExternalSymbolName("vodozemac_megolm_group_session_message_index")
private external fun vodozemac_megolm_group_session_message_index(group_session: NativePointer): Int

@ModuleImport("vodozemac", "vodozemac_megolm_group_session_session_config")
@ExternalSymbolName("vodozemac_megolm_group_session_session_config")
private external fun vodozemac_megolm_group_session_session_config(
    group_session: NativePointer
): NativePointer

@ModuleImport("vodozemac", "vodozemac_megolm_group_session_encrypt")
@ExternalSymbolName("vodozemac_megolm_group_session_encrypt")
private external fun vodozemac_megolm_group_session_encrypt(
    group_session: NativePointer,
    plaintext: InteropPointer,
    plaintext_size: Int
): NativePointer

@ModuleImport("vodozemac", "vodozemac_megolm_group_session_session_key")
@ExternalSymbolName("vodozemac_megolm_group_session_session_key")
private external fun vodozemac_megolm_group_session_session_key(
    group_session: NativePointer
): NativePointer

@ModuleImport("vodozemac", "vodozemac_megolm_group_session_pickle")
@ExternalSymbolName("vodozemac_megolm_group_session_pickle")
private external fun vodozemac_megolm_group_session_pickle(
    result: InteropPointer,
    group_session: NativePointer,
    pickle_key: InteropPointer?,
)

@ModuleImport("vodozemac", "vodozemac_megolm_group_session_from_pickle")
@ExternalSymbolName("vodozemac_megolm_group_session_from_pickle")
private external fun vodozemac_megolm_group_session_from_pickle(
    result: InteropPointer,
    ciphertext: InteropPointer,
    ciphertext_size: Int,
    pickle_key: InteropPointer?,
)

@ModuleImport("vodozemac", "vodozemac_megolm_group_session_from_libolm_pickle")
@ExternalSymbolName("vodozemac_megolm_group_session_from_libolm_pickle")
private external fun vodozemac_megolm_group_session_from_libolm_pickle(
    result: InteropPointer,
    pickle: InteropPointer,
    pickle_size: Int,
    pickle_key: InteropPointer,
    pickle_key_size: Int,
)
