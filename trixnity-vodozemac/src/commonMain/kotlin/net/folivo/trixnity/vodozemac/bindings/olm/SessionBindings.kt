@file:Import("vodozemac")

package net.folivo.trixnity.vodozemac.bindings.olm

import net.folivo.trixnity.vodozemac.ExternalSymbolName
import net.folivo.trixnity.vodozemac.Import
import net.folivo.trixnity.vodozemac.InitHook
import net.folivo.trixnity.vodozemac.ModuleImport
import net.folivo.trixnity.vodozemac.utils.InteropPointer
import net.folivo.trixnity.vodozemac.utils.NativePointer

internal object SessionBindings {

    init {
        InitHook()
    }

    fun free(session: NativePointer) = vodozemac_olm_session_free(session)

    fun sessionId(result: InteropPointer, session: NativePointer) =
        vodozemac_olm_session_session_id(result, session)

    fun hasReceivedMessage(session: NativePointer): Boolean =
        vodozemac_olm_session_has_received_message(session) == 1

    fun encrypt(
        result: InteropPointer,
        session: NativePointer,
        plaintext: InteropPointer,
        plaintextSize: Int,
    ) = vodozemac_olm_session_encrypt(result, session, plaintext, plaintextSize)

    fun sessionKeys(session: NativePointer): NativePointer =
        vodozemac_olm_session_session_keys(session)

    fun sessionConfig(session: NativePointer): NativePointer =
        vodozemac_olm_session_session_config(session)

    fun decrypt(
        result: InteropPointer,
        session: NativePointer,
        message: NativePointer,
        sessionKeys: NativePointer,
    ) = vodozemac_olm_session_decrypt(result, session, message, sessionKeys)

    fun pickle(result: InteropPointer, session: NativePointer, pickleKey: InteropPointer?) =
        vodozemac_olm_session_pickle(result, session, pickleKey)

    fun fromPickle(
        result: InteropPointer,
        ciphertext: InteropPointer,
        ciphertextSize: Int,
        pickleKey: InteropPointer?,
    ) = vodozemac_olm_session_from_pickle(result, ciphertext, ciphertextSize, pickleKey)

    fun fromLibolmPickle(
        result: InteropPointer,
        pickle: InteropPointer,
        pickleSize: Int,
        pickleKey: InteropPointer,
        pickleKeySize: Int,
    ) =
        vodozemac_olm_session_from_libolm_pickle(
            result, pickle, pickleSize, pickleKey, pickleKeySize)
}

@ModuleImport("vodozemac", "vodozemac_olm_session_free")
@ExternalSymbolName("vodozemac_olm_session_free")
private external fun vodozemac_olm_session_free(session: NativePointer)

@ModuleImport("vodozemac", "vodozemac_olm_session_session_id")
@ExternalSymbolName("vodozemac_olm_session_session_id")
private external fun vodozemac_olm_session_session_id(
    result: InteropPointer,
    session: NativePointer
)

@ModuleImport("vodozemac", "vodozemac_olm_session_has_received_message")
@ExternalSymbolName("vodozemac_olm_session_has_received_message")
private external fun vodozemac_olm_session_has_received_message(session: NativePointer): Int

@ModuleImport("vodozemac", "vodozemac_olm_session_encrypt")
@ExternalSymbolName("vodozemac_olm_session_encrypt")
private external fun vodozemac_olm_session_encrypt(
    result: InteropPointer,
    session: NativePointer,
    plaintext: InteropPointer,
    plaintext_size: Int,
)

@ModuleImport("vodozemac", "vodozemac_olm_session_session_keys")
@ExternalSymbolName("vodozemac_olm_session_session_keys")
private external fun vodozemac_olm_session_session_keys(session: NativePointer): NativePointer

@ModuleImport("vodozemac", "vodozemac_olm_session_session_config")
@ExternalSymbolName("vodozemac_olm_session_session_config")
private external fun vodozemac_olm_session_session_config(session: NativePointer): NativePointer

@ModuleImport("vodozemac", "vodozemac_olm_session_decrypt")
@ExternalSymbolName("vodozemac_olm_session_decrypt")
private external fun vodozemac_olm_session_decrypt(
    result: InteropPointer,
    session: NativePointer,
    message: NativePointer,
    session_keys: NativePointer,
)

@ModuleImport("vodozemac", "vodozemac_olm_session_pickle")
@ExternalSymbolName("vodozemac_olm_session_pickle")
private external fun vodozemac_olm_session_pickle(
    result: InteropPointer,
    session: NativePointer,
    pickle_key: InteropPointer? /* must be 32 bytes */
)

@ModuleImport("vodozemac", "vodozemac_olm_session_from_pickle")
@ExternalSymbolName("vodozemac_olm_session_from_pickle")
private external fun vodozemac_olm_session_from_pickle(
    result: InteropPointer,
    ciphertext: InteropPointer,
    ciphertext_size: Int,
    pickle_key: InteropPointer?, /* must be 32 bytes */
)

@ModuleImport("vodozemac", "vodozemac_olm_session_from_libolm_pickle")
@ExternalSymbolName("vodozemac_olm_session_from_libolm_pickle")
private external fun vodozemac_olm_session_from_libolm_pickle(
    result: InteropPointer,
    pickle: InteropPointer,
    pickle_size: Int,
    pickle_key: InteropPointer,
    pickle_key_size: Int,
)
