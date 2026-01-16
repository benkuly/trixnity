@file:Import("vodozemac")

package de.connect2x.trixnity.vodozemac.bindings.olm

import de.connect2x.trixnity.vodozemac.ExternalSymbolName
import de.connect2x.trixnity.vodozemac.Import
import de.connect2x.trixnity.vodozemac.InitHook
import de.connect2x.trixnity.vodozemac.ModuleImport
import de.connect2x.trixnity.vodozemac.utils.InteropPointer
import de.connect2x.trixnity.vodozemac.utils.NativePointer

internal object AccountBindings {

    init {
        InitHook()
    }

    fun new(): NativePointer = vodozemac_olm_account_new()

    fun free(account: NativePointer) = vodozemac_olm_account_free(account)

    fun identityKeys(result: InteropPointer, account: NativePointer) =
        vodozemac_olm_account_identity_keys(result, account)

    fun ed25519Key(account: NativePointer): NativePointer =
        vodozemac_olm_account_ed25519_key(account)

    fun curve25519Key(account: NativePointer): NativePointer =
        vodozemac_olm_account_curve25519_key(account)

    fun sign(account: NativePointer, message: InteropPointer, size: Int): NativePointer =
        vodozemac_olm_account_sign(account, message, size)

    fun maxNumberOfOneTimeKeys(account: NativePointer): Int =
        vodozemac_olm_account_max_number_of_one_time_keys(account)

    fun createOutboundSession(
        account: NativePointer,
        config: NativePointer,
        identityKey: NativePointer,
        oneTimeKey: NativePointer
    ): NativePointer =
        vodozemac_olm_account_create_outbound_session(account, config, identityKey, oneTimeKey)

    fun createInboundSession(
        result: InteropPointer,
        account: NativePointer,
        identityKey: NativePointer,
        message: NativePointer,
        sessionKeys: NativePointer
    ) =
        vodozemac_olm_account_create_inbound_session(
            result, account, identityKey, message, sessionKeys)

    fun generateOneTimeKeys(result: InteropPointer, account: NativePointer, count: Int) =
        vodozemac_olm_account_generate_one_time_keys(result, account, count)

    fun storedOneTimeKeyCount(account: NativePointer): Int =
        vodozemac_olm_account_stored_one_time_key_count(account)

    fun oneTimeKeys(result: InteropPointer, account: NativePointer) =
        vodozemac_olm_account_one_time_keys(result, account)

    fun generateFallbackKey(account: NativePointer): NativePointer =
        vodozemac_olm_account_generate_fallback_key(account)

    fun fallbackKey(result: InteropPointer, account: NativePointer) =
        vodozemac_olm_account_fallback_key(result, account)

    fun forgetFallbackKey(account: NativePointer): Boolean =
        vodozemac_olm_account_forget_fallback_key(account) == 1

    fun markKeysSsPublished(account: NativePointer) =
        vodozemac_olm_account_mark_keys_as_published(account)

    fun pickle(result: InteropPointer, account: NativePointer, pickleKey: InteropPointer?) =
        vodozemac_olm_account_pickle(result, account, pickleKey)

    fun fromPickle(
        result: InteropPointer,
        ciphertext: InteropPointer,
        ciphertextSize: Int,
        pickleKey: InteropPointer?
    ) = vodozemac_olm_account_from_pickle(result, ciphertext, ciphertextSize, pickleKey)

    fun toDehydratedDevice(result: InteropPointer, account: NativePointer, key: InteropPointer) =
        vodozemac_olm_account_to_dehydrated_device(result, account, key)

    fun fromDehydratedDevice(
        result: InteropPointer,
        ciphertext: InteropPointer,
        ciphertextSize: Int,
        nonce: InteropPointer,
        nonceSize: Int,
        key: InteropPointer,
    ) =
        vodozemac_olm_account_from_dehydrated_device(
            result, ciphertext, ciphertextSize, nonce, nonceSize, key)

    fun fromLibolmPickle(
        result: InteropPointer,
        pickle: InteropPointer,
        pickleSize: Int,
        pickleKey: InteropPointer,
        pickleKeySize: Int,
    ) =
        vodozemac_olm_account_from_libolm_pickle(
            result, pickle, pickleSize, pickleKey, pickleKeySize)
}

@ModuleImport("vodozemac", "vodozemac_olm_account_new")
@ExternalSymbolName("vodozemac_olm_account_new")
private external fun vodozemac_olm_account_new(): NativePointer

@ModuleImport("vodozemac", "vodozemac_olm_account_free")
@ExternalSymbolName("vodozemac_olm_account_free")
private external fun vodozemac_olm_account_free(account: NativePointer)

@ModuleImport("vodozemac", "vodozemac_olm_account_identity_keys")
@ExternalSymbolName("vodozemac_olm_account_identity_keys")
private external fun vodozemac_olm_account_identity_keys(
    result: InteropPointer,
    account: NativePointer
)

@ModuleImport("vodozemac", "vodozemac_olm_account_ed25519_key")
@ExternalSymbolName("vodozemac_olm_account_ed25519_key")
private external fun vodozemac_olm_account_ed25519_key(account: NativePointer): NativePointer

@ModuleImport("vodozemac", "vodozemac_olm_account_curve25519_key")
@ExternalSymbolName("vodozemac_olm_account_curve25519_key")
private external fun vodozemac_olm_account_curve25519_key(account: NativePointer): NativePointer

@ModuleImport("vodozemac", "vodozemac_olm_account_sign")
@ExternalSymbolName("vodozemac_olm_account_sign")
private external fun vodozemac_olm_account_sign(
    account: NativePointer,
    message: InteropPointer,
    message_size: Int
): NativePointer

@ModuleImport("vodozemac", "vodozemac_olm_account_max_number_of_one_time_keys")
@ExternalSymbolName("vodozemac_olm_account_max_number_of_one_time_keys")
private external fun vodozemac_olm_account_max_number_of_one_time_keys(account: NativePointer): Int

@ModuleImport("vodozemac", "vodozemac_olm_account_create_outbound_session")
@ExternalSymbolName("vodozemac_olm_account_create_outbound_session")
private external fun vodozemac_olm_account_create_outbound_session(
    account: NativePointer,
    session_config: NativePointer,
    identity_key: NativePointer,
    one_time_key: NativePointer
): NativePointer

@ModuleImport("vodozemac", "vodozemac_olm_account_create_inbound_session")
@ExternalSymbolName("vodozemac_olm_account_create_inbound_session")
private external fun vodozemac_olm_account_create_inbound_session(
    result: InteropPointer,
    account: NativePointer,
    their_identity_key: NativePointer,
    message: NativePointer,
    session_keys: NativePointer
)

@ModuleImport("vodozemac", "vodozemac_olm_account_generate_one_time_keys")
@ExternalSymbolName("vodozemac_olm_account_generate_one_time_keys")
private external fun vodozemac_olm_account_generate_one_time_keys(
    result: InteropPointer,
    account: NativePointer,
    count: Int
)

@ModuleImport("vodozemac", "vodozemac_olm_account_stored_one_time_key_count")
@ExternalSymbolName("vodozemac_olm_account_stored_one_time_key_count")
private external fun vodozemac_olm_account_stored_one_time_key_count(account: NativePointer): Int

@ModuleImport("vodozemac", "vodozemac_olm_account_one_time_keys")
@ExternalSymbolName("vodozemac_olm_account_one_time_keys")
private external fun vodozemac_olm_account_one_time_keys(
    result: InteropPointer,
    account: NativePointer
)

@ModuleImport("vodozemac", "vodozemac_olm_account_generate_fallback_key")
@ExternalSymbolName("vodozemac_olm_account_generate_fallback_key")
private external fun vodozemac_olm_account_generate_fallback_key(
    account: NativePointer
): NativePointer

@ModuleImport("vodozemac", "vodozemac_olm_account_fallback_key")
@ExternalSymbolName("vodozemac_olm_account_fallback_key")
private external fun vodozemac_olm_account_fallback_key(
    result: InteropPointer,
    account: NativePointer
)

@ModuleImport("vodozemac", "vodozemac_olm_account_forget_fallback_key")
@ExternalSymbolName("vodozemac_olm_account_forget_fallback_key")
private external fun vodozemac_olm_account_forget_fallback_key(account: NativePointer): Int

@ModuleImport("vodozemac", "vodozemac_olm_account_mark_keys_as_published")
@ExternalSymbolName("vodozemac_olm_account_mark_keys_as_published")
private external fun vodozemac_olm_account_mark_keys_as_published(account: NativePointer)

@ModuleImport("vodozemac", "vodozemac_olm_account_pickle")
@ExternalSymbolName("vodozemac_olm_account_pickle")
private external fun vodozemac_olm_account_pickle(
    result: InteropPointer,
    account: NativePointer,
    pickle_key: InteropPointer? /* Must be exactly 32 bytes */
)

@ModuleImport("vodozemac", "vodozemac_olm_account_from_pickle")
@ExternalSymbolName("vodozemac_olm_account_from_pickle")
private external fun vodozemac_olm_account_from_pickle(
    result: InteropPointer,
    ciphertext: InteropPointer,
    ciphertext_size: Int,
    pickle_key: InteropPointer?, /* Must be exactly 32 bytes */
)

@ModuleImport("vodozemac", "vodozemac_olm_account_to_dehydrated_device")
@ExternalSymbolName("vodozemac_olm_account_to_dehydrated_device")
private external fun vodozemac_olm_account_to_dehydrated_device(
    result: InteropPointer,
    account: NativePointer,
    key: InteropPointer,
)

@ModuleImport("vodozemac", "vodozemac_olm_account_from_dehydrated_device")
@ExternalSymbolName("vodozemac_olm_account_from_dehydrated_device")
private external fun vodozemac_olm_account_from_dehydrated_device(
    result: InteropPointer,
    ciphertext: InteropPointer,
    ciphertext_size: Int,
    nonce: InteropPointer,
    nonce_size: Int,
    key: InteropPointer,
)

@ModuleImport("vodozemac", "vodozemac_olm_account_from_libolm_pickle")
@ExternalSymbolName("vodozemac_olm_account_from_libolm_pickle")
private external fun vodozemac_olm_account_from_libolm_pickle(
    result: InteropPointer,
    pickle: InteropPointer,
    pickle_size: Int,
    pickle_key: InteropPointer,
    pickle_key_size: Int,
)
