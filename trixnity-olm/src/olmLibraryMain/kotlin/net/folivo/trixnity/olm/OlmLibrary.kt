@file:Suppress("FunctionName")

package net.folivo.trixnity.olm

expect object OlmLibrary {

    fun inbound_group_session(): OlmInboundGroupSessionPointer

    fun inbound_group_session_last_error(session: OlmInboundGroupSessionPointer): String?

    fun clear_inbound_group_session(session: OlmInboundGroupSessionPointer): ULong

    fun pickle_inbound_group_session_length(session: OlmInboundGroupSessionPointer): ULong

    fun pickle_inbound_group_session(
        session: OlmInboundGroupSessionPointer,
        key: ByteArray,
        pickled: ByteArray,
    ): ULong

    fun unpickle_inbound_group_session(
        session: OlmInboundGroupSessionPointer,
        key: ByteArray,
        pickled: ByteArray,
    ): ULong

    fun init_inbound_group_session(
        session: OlmInboundGroupSessionPointer,
        key: ByteArray,
    ): ULong

    fun import_inbound_group_session(
        session: OlmInboundGroupSessionPointer,
        key: ByteArray,
    ): ULong

    fun group_decrypt_max_plaintext_length(
        session: OlmInboundGroupSessionPointer,
        message: ByteArray,
    ): ULong

    fun group_decrypt(
        session: OlmInboundGroupSessionPointer,
        message: ByteArray,
        plainText: ByteArray,
        messageIndex: MutableWrapper<UInt>
    ): ULong

    fun inbound_group_session_id_length(session: OlmInboundGroupSessionPointer): ULong

    fun inbound_group_session_id(
        session: OlmInboundGroupSessionPointer,
        sessionId: ByteArray,
    ): ULong

    fun inbound_group_session_first_known_index(session: OlmInboundGroupSessionPointer): UInt

    fun inbound_group_session_is_verified(session: OlmInboundGroupSessionPointer): Int

    fun export_inbound_group_session_length(session: OlmInboundGroupSessionPointer): ULong

    fun export_inbound_group_session(
        session: OlmInboundGroupSessionPointer,
        key: ByteArray,
        messageIndex: UInt
    ): ULong

    fun outbound_group_session(): OlmOutboundGroupSessionPointer

    fun outbound_group_session_last_error(session: OlmOutboundGroupSessionPointer): String?

    fun clear_outbound_group_session(session: OlmOutboundGroupSessionPointer): ULong

    fun pickle_outbound_group_session_length(session: OlmOutboundGroupSessionPointer): ULong

    fun pickle_outbound_group_session(
        session: OlmOutboundGroupSessionPointer,
        key: ByteArray,
        pickled: ByteArray,
    ): ULong

    fun unpickle_outbound_group_session(
        session: OlmOutboundGroupSessionPointer,
        key: ByteArray,
        pickled: ByteArray,
    ): ULong

    fun init_outbound_group_session_random_length(session: OlmOutboundGroupSessionPointer): ULong

    fun init_outbound_group_session(
        session: OlmOutboundGroupSessionPointer,
        random: ByteArray?,
    ): ULong

    fun group_encrypt_message_length(
        session: OlmOutboundGroupSessionPointer,
        plainTextLength: ULong
    ): ULong

    fun group_encrypt(
        session: OlmOutboundGroupSessionPointer,
        plainText: ByteArray,
        message: ByteArray,
    ): ULong

    fun outbound_group_session_id_length(session: OlmOutboundGroupSessionPointer): ULong

    fun outbound_group_session_id(
        session: OlmOutboundGroupSessionPointer,
        sessionId: ByteArray,
    ): ULong

    fun outbound_group_session_message_index(session: OlmOutboundGroupSessionPointer): UInt

    fun outbound_group_session_key_length(session: OlmOutboundGroupSessionPointer): ULong

    fun outbound_group_session_key(
        session: OlmOutboundGroupSessionPointer,
        key: ByteArray,
    ): ULong

    fun get_library_version(
        major: MutableWrapper<UInt>,
        minor: MutableWrapper<UInt>,
        patch: MutableWrapper<UInt>
    )

    fun account(): OlmAccountPointer

    fun session(): OlmSessionPointer

    fun utility(): OlmUtilityPointer

    fun error(): ULong

    fun account_last_error(account: OlmAccountPointer): String?

    fun session_last_error(session: OlmSessionPointer): String?

    fun utility_last_error(utility: OlmUtilityPointer): String?

    fun clear_account(account: OlmAccountPointer): ULong

    fun clear_session(session: OlmSessionPointer): ULong

    fun clear_utility(utility: OlmUtilityPointer): ULong

    fun pickle_account_length(account: OlmAccountPointer): ULong

    fun pickle_session_length(session: OlmSessionPointer): ULong

    fun pickle_account(
        account: OlmAccountPointer,
        key: ByteArray,
        pickled: ByteArray,
    ): ULong

    fun pickle_session(
        session: OlmSessionPointer,
        key: ByteArray,
        pickled: ByteArray,
    ): ULong

    fun unpickle_account(
        account: OlmAccountPointer,
        key: ByteArray,
        pickled: ByteArray,
    ): ULong

    fun unpickle_session(
        session: OlmSessionPointer,
        key: ByteArray,
        pickled: ByteArray,
    ): ULong

    fun create_account_random_length(account: OlmAccountPointer): ULong

    fun create_account(
        account: OlmAccountPointer,
        random: ByteArray?,
    ): ULong

    fun account_identity_keys_length(account: OlmAccountPointer): ULong

    fun account_identity_keys(
        account: OlmAccountPointer,
        identityKeys: ByteArray,
    ): ULong

    fun account_signature_length(account: OlmAccountPointer): ULong

    fun account_sign(
        account: OlmAccountPointer,
        message: ByteArray,
        signature: ByteArray,
    ): ULong

    fun account_one_time_keys_length(account: OlmAccountPointer): ULong

    fun account_one_time_keys(
        account: OlmAccountPointer,
        oneTimeKeys: ByteArray,
    ): ULong

    fun account_mark_keys_as_published(account: OlmAccountPointer): ULong

    fun account_max_number_of_one_time_keys(account: OlmAccountPointer): ULong

    fun account_generate_one_time_keys_random_length(
        account: OlmAccountPointer,
        numberOfKeys: ULong
    ): ULong

    fun account_generate_one_time_keys(
        account: OlmAccountPointer,
        numberOfKeys: ULong,
        random: ByteArray?,
    ): ULong

    fun account_generate_fallback_key_random_length(
        account: OlmAccountPointer
    ): ULong

    fun account_generate_fallback_key(
        account: OlmAccountPointer,
        random: ByteArray?,
    ): ULong

    fun account_forget_old_fallback_key(account: OlmAccountPointer)

    fun account_unpublished_fallback_key_length(
        account: OlmAccountPointer
    ): ULong

    fun account_unpublished_fallback_key(
        account: OlmAccountPointer,
        fallbackKey: ByteArray,
    ): ULong

    fun create_outbound_session_random_length(session: OlmSessionPointer): ULong

    fun create_outbound_session(
        session: OlmSessionPointer,
        account: OlmAccountPointer,
        theirIdentityKey: ByteArray,
        theirOneTimeKey: ByteArray,
        random: ByteArray?,
    ): ULong

    fun create_inbound_session(
        session: OlmSessionPointer,
        account: OlmAccountPointer,
        oneTimeKeyMessage: ByteArray,
    ): ULong

    fun create_inbound_session_from(
        session: OlmSessionPointer,
        account: OlmAccountPointer,
        theirIdentityKey: ByteArray,
        oneTimeKeyMessage: ByteArray,
    ): ULong

    fun session_id_length(session: OlmSessionPointer): ULong

    fun session_id(
        session: OlmSessionPointer,
        id: ByteArray,
    ): ULong

    fun session_has_received_message(session: OlmSessionPointer): Int

    fun session_describe(
        session: OlmSessionPointer,
        description: ByteArray,
    )

    fun matches_inbound_session(
        session: OlmSessionPointer,
        oneTimeKeyMessage: ByteArray,
    ): ULong

    fun matches_inbound_session_from(
        session: OlmSessionPointer,
        theirIdentityKey: ByteArray,
        oneTimeKeyMessage: ByteArray,
    ): ULong

    fun remove_one_time_keys(account: OlmAccountPointer, session: OlmSessionPointer): ULong

    fun encrypt_message_type(session: OlmSessionPointer): ULong

    fun encrypt_random_length(session: OlmSessionPointer): ULong

    fun encrypt_message_length(session: OlmSessionPointer, plainTextLength: ULong): ULong

    fun encrypt(
        session: OlmSessionPointer,
        plainText: ByteArray,
        random: ByteArray?,
        message: ByteArray,
    ): ULong

    fun decrypt_max_plaintext_length(
        session: OlmSessionPointer,
        messageType: ULong,
        message: ByteArray,
    ): ULong

    fun decrypt(
        session: OlmSessionPointer,
        messageType: ULong,
        message: ByteArray,
        plainText: ByteArray,
    ): ULong

    fun sha256_length(utility: OlmUtilityPointer): ULong

    fun sha256(
        utility: OlmUtilityPointer,
        input: ByteArray,
        output: ByteArray,
    ): ULong

    fun ed25519_verify(
        utility: OlmUtilityPointer,
        key: ByteArray,
        message: ByteArray,
        signature: ByteArray,
    ): ULong

    fun sas(): OlmSASPointer

    fun sas_last_error(sas: OlmSASPointer): String?

    fun clear_sas(sas: OlmSASPointer): ULong

    fun create_sas_random_length(sas: OlmSASPointer): ULong

    fun create_sas(
        sas: OlmSASPointer,
        random: ByteArray?,
    ): ULong

    fun sas_pubkey_length(sas: OlmSASPointer): ULong

    fun sas_get_pubkey(
        sas: OlmSASPointer,
        pubkey: ByteArray,
    ): ULong

    fun sas_set_their_key(
        sas: OlmSASPointer,
        theirKey: ByteArray,
    ): ULong

    fun sas_is_their_key_set(sas: OlmSASPointer): Int

    fun sas_generate_bytes(
        sas: OlmSASPointer,
        info: ByteArray,
        output: ByteArray,
    ): ULong

    fun sas_mac_length(sas: OlmSASPointer): ULong

    fun sas_calculate_mac(
        sas: OlmSASPointer,
        input: ByteArray,
        info: ByteArray,
        mac: ByteArray,
    ): ULong

    fun sas_calculate_mac_fixed_base64(
        sas: OlmSASPointer,
        input: ByteArray,
        info: ByteArray,
        mac: ByteArray,
    ): ULong

    fun sas_calculate_mac_ULong_kdf(
        sas: OlmSASPointer,
        input: ByteArray,
        info: ByteArray,
        mac: ByteArray,
    ): ULong

    fun pk_encryption(): OlmPkEncryptionPointer

    fun pk_encryption_last_error(encryption: OlmPkEncryptionPointer): String?

    fun clear_pk_encryption(encryption: OlmPkEncryptionPointer): ULong

    fun pk_encryption_set_recipient_key(
        encryption: OlmPkEncryptionPointer,
        publicKey: ByteArray,
    ): ULong

    fun pk_ciphertext_length(encryption: OlmPkEncryptionPointer, plainTextLength: ULong): ULong

    fun pk_mac_length(encryption: OlmPkEncryptionPointer): ULong

    fun pk_key_length(): ULong

    fun pk_encrypt_random_length(encryption: OlmPkEncryptionPointer): ULong

    fun pk_encrypt(
        encryption: OlmPkEncryptionPointer,
        plainText: ByteArray,
        cipherText: ByteArray,
        mac: ByteArray,
        ephemeralKey: ByteArray,
        random: ByteArray?,
    ): ULong

    fun pk_decryption(): OlmPkDecryptionPointer

    fun pk_decryption_last_error(decryption: OlmPkDecryptionPointer): String?

    fun clear_pk_decryption(decryption: OlmPkDecryptionPointer): ULong

    fun pk_private_key_length(): ULong

    fun pk_key_from_private(
        decryption: OlmPkDecryptionPointer,
        pubkey: ByteArray,
        privkey: ByteArray,
    ): ULong

    fun pickle_pk_decryption_length(decryption: OlmPkDecryptionPointer): ULong

    fun pickle_pk_decryption(
        decryption: OlmPkDecryptionPointer,
        key: ByteArray,
        pickled: ByteArray,
    ): ULong

    fun unpickle_pk_decryption(
        decryption: OlmPkDecryptionPointer,
        key: ByteArray,
        pickled: ByteArray,
        pubkey: ByteArray,
    ): ULong

    fun pk_max_plaintext_length(
        decryption: OlmPkDecryptionPointer,
        cipherTextLength: ULong
    ): ULong

    fun pk_decrypt(
        decryption: OlmPkDecryptionPointer,
        ephemeralKey: ByteArray,
        mac: ByteArray,
        cipherText: ByteArray,
        plainText: ByteArray,
    ): ULong

    fun pk_get_private_key(
        decryption: OlmPkDecryptionPointer,
        privateKey: ByteArray,
    ): ULong

    fun pk_signing(): OlmPkSigningPointer

    fun pk_signing_last_error(sign: OlmPkSigningPointer): String?

    fun clear_pk_signing(sign: OlmPkSigningPointer): ULong

    fun pk_signing_key_from_seed(
        sign: OlmPkSigningPointer,
        pubkey: ByteArray,
        seed: ByteArray,
    ): ULong

    fun pk_signing_seed_length(): ULong

    fun pk_signing_public_key_length(): ULong

    fun pk_signature_length(): ULong

    fun pk_sign(
        sign: OlmPkSigningPointer,
        message: ByteArray,
        signature: ByteArray,
    ): ULong

}