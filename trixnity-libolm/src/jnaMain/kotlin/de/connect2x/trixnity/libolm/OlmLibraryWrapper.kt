/*
 * This file was copied from https://github.com/Dominaezzz/matrix-kt
 * and has been slightly modified.
 *
 * Licenced under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("FunctionName")

package de.connect2x.trixnity.libolm

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.error

private val log = Logger("de.connect2x.trixnity.libolm.OlmLibraryWrapper")

object OlmLibraryWrapper : Library {
    private const val JNA_LIBRARY_NAME: String = "olm"

    init {
        try {
            Native.register(OlmLibraryWrapper::class.java, JNA_LIBRARY_NAME)
        } catch (exception: Throwable) {
            log.error(exception) { "could not load olm library" }
            throw exception
        }
    }


    external fun olm_inbound_group_session_size(): NativeSize

    external fun olm_inbound_group_session(memory: Pointer?): OlmInboundGroupSessionPointer?

    external fun olm_inbound_group_session_last_error(session: OlmInboundGroupSessionPointer?): String?

    external fun olm_clear_inbound_group_session(session: OlmInboundGroupSessionPointer?): NativeSize

    external fun olm_pickle_inbound_group_session_length(session: OlmInboundGroupSessionPointer?): NativeSize

    external fun olm_pickle_inbound_group_session(
        session: OlmInboundGroupSessionPointer?,
        key: Pointer?,
        key_length: NativeSize,
        pickled: Pointer?,
        pickled_length: NativeSize
    ): NativeSize

    external fun olm_unpickle_inbound_group_session(
        session: OlmInboundGroupSessionPointer?,
        key: Pointer?,
        key_length: NativeSize,
        pickled: Pointer?,
        pickled_length: NativeSize
    ): NativeSize

    external fun olm_init_inbound_group_session(
        session: OlmInboundGroupSessionPointer?,
        session_key: Pointer?,
        session_key_length: NativeSize
    ): NativeSize

    external fun olm_import_inbound_group_session(
        session: OlmInboundGroupSessionPointer?,
        session_key: Pointer?,
        session_key_length: NativeSize
    ): NativeSize

    external fun olm_group_decrypt_max_plaintext_length(
        session: OlmInboundGroupSessionPointer?,
        message: Pointer?,
        message_length: NativeSize
    ): NativeSize

    external fun olm_group_decrypt(
        session: OlmInboundGroupSessionPointer?,
        message: Pointer?,
        message_length: NativeSize,
        plaintext: Pointer?,
        max_plaintext_length: NativeSize,
        message_index: IntByReference?
    ): NativeSize

    external fun olm_inbound_group_session_id_length(session: OlmInboundGroupSessionPointer?): NativeSize

    external fun olm_inbound_group_session_id(
        session: OlmInboundGroupSessionPointer?,
        id: Pointer?,
        id_length: NativeSize
    ): NativeSize

    external fun olm_inbound_group_session_first_known_index(session: OlmInboundGroupSessionPointer?): Int

    external fun olm_inbound_group_session_is_verified(session: OlmInboundGroupSessionPointer?): Int

    external fun olm_export_inbound_group_session_length(session: OlmInboundGroupSessionPointer?): NativeSize

    external fun olm_export_inbound_group_session(
        session: OlmInboundGroupSessionPointer?,
        key: Pointer?,
        key_length: NativeSize,
        message_index: Int
    ): NativeSize

    external fun olm_outbound_group_session_size(): NativeSize

    external fun olm_outbound_group_session(memory: Pointer?): OlmOutboundGroupSessionPointer?

    external fun olm_outbound_group_session_last_error(session: OlmOutboundGroupSessionPointer?): String?

    external fun olm_clear_outbound_group_session(session: OlmOutboundGroupSessionPointer?): NativeSize

    external fun olm_pickle_outbound_group_session_length(session: OlmOutboundGroupSessionPointer?): NativeSize

    external fun olm_pickle_outbound_group_session(
        session: OlmOutboundGroupSessionPointer?,
        key: Pointer?,
        key_length: NativeSize,
        pickled: Pointer?,
        pickled_length: NativeSize
    ): NativeSize

    external fun olm_unpickle_outbound_group_session(
        session: OlmOutboundGroupSessionPointer?,
        key: Pointer?,
        key_length: NativeSize,
        pickled: Pointer?,
        pickled_length: NativeSize
    ): NativeSize

    external fun olm_init_outbound_group_session_random_length(session: OlmOutboundGroupSessionPointer?): NativeSize

    external fun olm_init_outbound_group_session(
        session: OlmOutboundGroupSessionPointer?,
        random: Pointer?,
        random_length: NativeSize
    ): NativeSize

    external fun olm_group_encrypt_message_length(
        session: OlmOutboundGroupSessionPointer?,
        plaintext_length: NativeSize
    ): NativeSize

    external fun olm_group_encrypt(
        session: OlmOutboundGroupSessionPointer?,
        plaintext: Pointer?,
        plaintext_length: NativeSize,
        message: Pointer?,
        message_length: NativeSize
    ): NativeSize

    external fun olm_outbound_group_session_id_length(session: OlmOutboundGroupSessionPointer?): NativeSize

    external fun olm_outbound_group_session_id(
        session: OlmOutboundGroupSessionPointer?,
        id: Pointer?,
        id_length: NativeSize
    ): NativeSize

    external fun olm_outbound_group_session_message_index(session: OlmOutboundGroupSessionPointer?): Int

    external fun olm_outbound_group_session_key_length(session: OlmOutboundGroupSessionPointer?): NativeSize

    external fun olm_outbound_group_session_key(
        session: OlmOutboundGroupSessionPointer?,
        key: Pointer?,
        key_length: NativeSize
    ): NativeSize

    external fun olm_get_library_version(
        major: IntByReference,
        minor: IntByReference,
        patch: IntByReference
    )

    external fun olm_account_size(): NativeSize

    external fun olm_session_size(): NativeSize

    external fun olm_utility_size(): NativeSize

    external fun olm_account(memory: Pointer?): OlmAccountPointer?

    external fun olm_session(memory: Pointer?): OlmSessionPointer?

    external fun olm_utility(memory: Pointer?): OlmUtilityPointer?

    external fun olm_error(): NativeSize

    external fun olm_account_last_error(account: OlmAccountPointer?): String?

    external fun olm_session_last_error(session: OlmSessionPointer?): String?

    external fun olm_utility_last_error(utility: OlmUtilityPointer?): String?

    external fun olm_clear_account(account: OlmAccountPointer?): NativeSize

    external fun olm_clear_session(session: OlmSessionPointer?): NativeSize

    external fun olm_clear_utility(utility: OlmUtilityPointer?): NativeSize

    external fun olm_pickle_account_length(account: OlmAccountPointer?): NativeSize

    external fun olm_pickle_session_length(session: OlmSessionPointer?): NativeSize

    external fun olm_pickle_account(
        account: OlmAccountPointer?,
        key: Pointer?,
        key_length: NativeSize,
        pickled: Pointer?,
        pickled_length: NativeSize
    ): NativeSize

    external fun olm_pickle_session(
        session: OlmSessionPointer?,
        key: Pointer?,
        key_length: NativeSize,
        pickled: Pointer?,
        pickled_length: NativeSize
    ): NativeSize

    external fun olm_unpickle_account(
        account: OlmAccountPointer?,
        key: Pointer?,
        key_length: NativeSize,
        pickled: Pointer?,
        pickled_length: NativeSize
    ): NativeSize

    external fun olm_unpickle_session(
        session: OlmSessionPointer?,
        key: Pointer?,
        key_length: NativeSize,
        pickled: Pointer?,
        pickled_length: NativeSize
    ): NativeSize

    external fun olm_create_account_random_length(account: OlmAccountPointer?): NativeSize

    external fun olm_create_account(
        account: OlmAccountPointer?,
        random: Pointer?,
        random_length: NativeSize
    ): NativeSize

    external fun olm_account_identity_keys_length(account: OlmAccountPointer?): NativeSize

    external fun olm_account_identity_keys(
        account: OlmAccountPointer?,
        identity_keys: Pointer?,
        identity_key_length: NativeSize
    ): NativeSize

    external fun olm_account_signature_length(account: OlmAccountPointer?): NativeSize

    external fun olm_account_sign(
        account: OlmAccountPointer?,
        message: Pointer?,
        message_length: NativeSize,
        signature: Pointer?,
        signature_length: NativeSize
    ): NativeSize

    external fun olm_account_one_time_keys_length(account: OlmAccountPointer?): NativeSize

    external fun olm_account_one_time_keys(
        account: OlmAccountPointer?,
        one_time_keys: Pointer?,
        one_time_keys_length: NativeSize
    ): NativeSize

    external fun olm_account_mark_keys_as_published(account: OlmAccountPointer?): NativeSize

    external fun olm_account_max_number_of_one_time_keys(account: OlmAccountPointer?): NativeSize

    external fun olm_account_generate_one_time_keys_random_length(
        account: OlmAccountPointer?,
        number_of_keys: NativeSize
    ): NativeSize

    external fun olm_account_generate_one_time_keys(
        account: OlmAccountPointer?,
        number_of_keys: NativeSize,
        random: Pointer?,
        random_length: NativeSize
    ): NativeSize

    external fun olm_account_generate_fallback_key_random_length(
        account: OlmAccountPointer?
    ): NativeSize

    external fun olm_account_generate_fallback_key(
        account: OlmAccountPointer?,
        random: Pointer?,
        random_length: NativeSize
    ): NativeSize

    external fun olm_account_forget_old_fallback_key(
        account: OlmAccountPointer?
    )

    external fun olm_account_unpublished_fallback_key_length(
        account: OlmAccountPointer?
    ): NativeSize

    external fun olm_account_unpublished_fallback_key(
        account: OlmAccountPointer?,
        fallback_key: Pointer?,
        fallback_key_size: NativeSize
    ): NativeSize

    external fun olm_create_outbound_session_random_length(session: OlmSessionPointer?): NativeSize

    external fun olm_create_outbound_session(
        session: OlmSessionPointer?,
        account: OlmAccountPointer?,
        their_identity_key: Pointer?,
        their_identity_key_length: NativeSize,
        their_one_time_key: Pointer?,
        their_one_time_key_length: NativeSize,
        random: Pointer?,
        random_length: NativeSize
    ): NativeSize

    external fun olm_create_inbound_session(
        session: OlmSessionPointer?,
        account: OlmAccountPointer?,
        one_time_key_message: Pointer?,
        message_length: NativeSize
    ): NativeSize

    external fun olm_create_inbound_session_from(
        session: OlmSessionPointer?,
        account: OlmAccountPointer?,
        their_identity_key: Pointer?,
        their_identity_key_length: NativeSize,
        one_time_key_message: Pointer?,
        message_length: NativeSize
    ): NativeSize

    external fun olm_session_id_length(session: OlmSessionPointer?): NativeSize

    external fun olm_session_id(
        session: OlmSessionPointer?,
        id: Pointer?,
        id_length: NativeSize
    ): NativeSize

    external fun olm_session_has_received_message(session: OlmSessionPointer?): Int

    external fun olm_session_describe(
        session: OlmSessionPointer?,
        buf: Pointer?,
        buflen: NativeSize
    )

    external fun olm_matches_inbound_session(
        session: OlmSessionPointer?,
        one_time_key_message: Pointer?,
        message_length: NativeSize
    ): NativeSize

    external fun olm_matches_inbound_session_from(
        session: OlmSessionPointer?,
        their_identity_key: Pointer?,
        their_identity_key_length: NativeSize,
        one_time_key_message: Pointer?,
        message_length: NativeSize
    ): NativeSize

    external fun olm_remove_one_time_keys(account: OlmAccountPointer?, session: OlmSessionPointer?): NativeSize

    external fun olm_encrypt_message_type(session: OlmSessionPointer?): NativeSize

    external fun olm_encrypt_random_length(session: OlmSessionPointer?): NativeSize

    external fun olm_encrypt_message_length(session: OlmSessionPointer?, plaintext_length: NativeSize): NativeSize

    external fun olm_encrypt(
        session: OlmSessionPointer?,
        plaintext: Pointer?,
        plaintext_length: NativeSize,
        random: Pointer?,
        random_length: NativeSize,
        message: Pointer?,
        message_length: NativeSize
    ): NativeSize

    external fun olm_decrypt_max_plaintext_length(
        session: OlmSessionPointer?,
        message_type: NativeSize,
        message: Pointer?,
        message_length: NativeSize
    ): NativeSize

    external fun olm_decrypt(
        session: OlmSessionPointer?,
        message_type: NativeSize,
        message: Pointer?,
        message_length: NativeSize,
        plaintext: Pointer?,
        max_plaintext_length: NativeSize
    ): NativeSize

    external fun olm_sha256_length(utility: OlmUtilityPointer?): NativeSize

    external fun olm_sha256(
        utility: OlmUtilityPointer?,
        input: Pointer?,
        input_length: NativeSize,
        output: Pointer?,
        output_length: NativeSize
    ): NativeSize

    external fun olm_ed25519_verify(
        utility: OlmUtilityPointer?,
        key: Pointer?,
        key_length: NativeSize,
        message: Pointer?,
        message_length: NativeSize,
        signature: Pointer?,
        signature_length: NativeSize
    ): NativeSize

    external fun olm_sas_last_error(sas: OlmSASPointer?): String?

    external fun olm_sas_size(): NativeSize

    external fun olm_sas(memory: Pointer?): OlmSASPointer?

    external fun olm_clear_sas(sas: OlmSASPointer?): NativeSize

    external fun olm_create_sas_random_length(sas: OlmSASPointer?): NativeSize

    external fun olm_create_sas(
        sas: OlmSASPointer?,
        random: Pointer?,
        random_length: NativeSize
    ): NativeSize

    external fun olm_sas_pubkey_length(sas: OlmSASPointer?): NativeSize

    external fun olm_sas_get_pubkey(
        sas: OlmSASPointer?,
        pubkey: Pointer?,
        pubkey_length: NativeSize
    ): NativeSize

    external fun olm_sas_set_their_key(
        sas: OlmSASPointer?,
        their_key: Pointer?,
        their_key_length: NativeSize
    ): NativeSize

    external fun olm_sas_is_their_key_set(sas: OlmSASPointer?): Int

    external fun olm_sas_generate_bytes(
        sas: OlmSASPointer?,
        info: Pointer?,
        info_length: NativeSize,
        output: Pointer?,
        output_length: NativeSize
    ): NativeSize

    external fun olm_sas_mac_length(sas: OlmSASPointer?): NativeSize

    external fun olm_sas_calculate_mac(
        sas: OlmSASPointer?,
        input: Pointer?,
        input_length: NativeSize,
        info: Pointer?,
        info_length: NativeSize,
        mac: Pointer?,
        mac_length: NativeSize
    ): NativeSize

    external fun olm_sas_calculate_mac_fixed_base64(
        sas: OlmSASPointer?,
        input: Pointer?,
        input_length: NativeSize,
        info: Pointer?,
        info_length: NativeSize,
        mac: Pointer?,
        mac_length: NativeSize
    ): NativeSize

    external fun olm_sas_calculate_mac_long_kdf(
        sas: OlmSASPointer?,
        input: Pointer?,
        input_length: NativeSize,
        info: Pointer?,
        info_length: NativeSize,
        mac: Pointer?,
        mac_length: NativeSize
    ): NativeSize

    external fun olm_pk_encryption_size(): NativeSize

    external fun olm_pk_encryption(memory: Pointer?): OlmPkEncryptionPointer?

    external fun olm_pk_encryption_last_error(encryption: OlmPkEncryptionPointer?): String?

    external fun olm_clear_pk_encryption(encryption: OlmPkEncryptionPointer?): NativeSize

    external fun olm_pk_encryption_set_recipient_key(
        encryption: OlmPkEncryptionPointer?,
        _key: Pointer?,
        _key_length: NativeSize
    ): NativeSize

    external fun olm_pk_ciphertext_length(encryption: OlmPkEncryptionPointer?, plaintext_length: NativeSize): NativeSize

    external fun olm_pk_mac_length(encryption: OlmPkEncryptionPointer?): NativeSize

    external fun olm_pk_key_length(): NativeSize

    external fun olm_pk_encrypt_random_length(encryption: OlmPkEncryptionPointer?): NativeSize

    external fun olm_pk_encrypt(
        encryption: OlmPkEncryptionPointer?,
        plaintext: Pointer?,
        plaintext_length: NativeSize,
        cipherText: Pointer?,
        cipherText_length: NativeSize,
        mac: Pointer?,
        mac_length: NativeSize,
        ephemeral_key: Pointer?,
        ephemeral_key_size: NativeSize,
        random: Pointer?,
        random_length: NativeSize
    ): NativeSize

    external fun olm_pk_decryption_size(): NativeSize

    external fun olm_pk_decryption(memory: Pointer?): OlmPkDecryptionPointer?

    external fun olm_pk_decryption_last_error(decryption: OlmPkDecryptionPointer?): String?

    external fun olm_clear_pk_decryption(decryption: OlmPkDecryptionPointer?): NativeSize

    external fun olm_pk_private_key_length(): NativeSize


    external fun olm_pk_key_from_private(
        decryption: OlmPkDecryptionPointer?,
        pubkey: Pointer?,
        pubkey_length: NativeSize,
        privkey: Pointer?,
        privkey_length: NativeSize
    ): NativeSize

    external fun olm_pickle_pk_decryption_length(decryption: OlmPkDecryptionPointer?): NativeSize

    external fun olm_pickle_pk_decryption(
        decryption: OlmPkDecryptionPointer?,
        key: Pointer?,
        key_length: NativeSize,
        pickled: Pointer?,
        pickled_length: NativeSize
    ): NativeSize

    external fun olm_unpickle_pk_decryption(
        decryption: OlmPkDecryptionPointer?,
        key: Pointer?,
        key_length: NativeSize,
        pickled: Pointer?,
        pickled_length: NativeSize,
        pubkey: Pointer?,
        pubkey_length: NativeSize
    ): NativeSize

    external fun olm_pk_max_plaintext_length(
        decryption: OlmPkDecryptionPointer?,
        cipherText_length: NativeSize
    ): NativeSize

    external fun olm_pk_decrypt(
        decryption: OlmPkDecryptionPointer?,
        ephemeral_key: Pointer?,
        ephemeral_key_length: NativeSize,
        mac: Pointer?,
        mac_length: NativeSize,
        cipherText: Pointer?,
        cipherText_length: NativeSize,
        plaintext: Pointer?,
        max_plaintext_length: NativeSize
    ): NativeSize

    external fun olm_pk_get_private_key(
        decryption: OlmPkDecryptionPointer?,
        private_key: Pointer?,
        private_key_length: NativeSize
    ): NativeSize

    external fun olm_pk_signing_size(): NativeSize

    external fun olm_pk_signing(memory: Pointer?): OlmPkSigningPointer?

    external fun olm_pk_signing_last_error(sign: OlmPkSigningPointer?): String?

    external fun olm_clear_pk_signing(sign: OlmPkSigningPointer?): NativeSize

    external fun olm_pk_signing_key_from_seed(
        sign: OlmPkSigningPointer?,
        pubkey: Pointer?,
        pubkey_length: NativeSize,
        seed: Pointer?,
        seed_length: NativeSize
    ): NativeSize

    external fun olm_pk_signing_seed_length(): NativeSize

    external fun olm_pk_signing_public_key_length(): NativeSize

    external fun olm_pk_signature_length(): NativeSize

    external fun olm_pk_sign(
        sign: OlmPkSigningPointer?,
        message: Pointer?,
        message_length: NativeSize,
        signature: Pointer?,
        signature_length: NativeSize
    ): NativeSize
}
