@file:Suppress("FunctionName")

package net.folivo.trixnity.olm

import kotlinx.cinterop.*
import org.matrix.olm.*

@OptIn(ExperimentalUnsignedTypes::class)
actual object OlmLibrary {

    actual fun error(): ULong = olm_error()

    actual fun inbound_group_session(): OlmInboundGroupSessionPointer =
        OlmInboundGroupSessionPointer(genericInit(::olm_inbound_group_session, olm_inbound_group_session_size()))

    actual fun inbound_group_session_last_error(session: OlmInboundGroupSessionPointer): String? =
        olm_inbound_group_session_last_error(session.ptr)?.toKString()

    actual fun clear_inbound_group_session(session: OlmInboundGroupSessionPointer): ULong =
        olm_clear_inbound_group_session(session.ptr)

    actual fun pickle_inbound_group_session_length(session: OlmInboundGroupSessionPointer): ULong =
        olm_pickle_inbound_group_session_length(session.ptr)

    actual fun pickle_inbound_group_session(
        session: OlmInboundGroupSessionPointer,
        key: ByteArray,
        pickled: ByteArray,
    ): ULong = olm_pickle_inbound_group_session(
        session.ptr,
        key.refTo(0), key.usize(),
        pickled.refTo(0), pickled.usize()
    )

    actual fun unpickle_inbound_group_session(
        session: OlmInboundGroupSessionPointer,
        key: ByteArray,
        pickled: ByteArray,
    ): ULong = olm_unpickle_inbound_group_session(
        session.ptr,
        key.refTo(0), key.usize(),
        pickled.refTo(0), pickled.usize()
    )

    actual fun init_inbound_group_session(
        session: OlmInboundGroupSessionPointer,
        key: ByteArray,
    ): ULong =
        olm_init_inbound_group_session(session.ptr, key.asUByteArray().refTo(0), key.usize())

    actual fun import_inbound_group_session(
        session: OlmInboundGroupSessionPointer,
        key: ByteArray,
    ): ULong =
        olm_import_inbound_group_session(session.ptr, key.asUByteArray().refTo(0), key.usize())

    actual fun group_decrypt_max_plaintext_length(
        session: OlmInboundGroupSessionPointer,
        message: ByteArray,
    ): ULong =
        olm_group_decrypt_max_plaintext_length(session.ptr, message.asUByteArray().refTo(0), message.usize())

    actual fun group_decrypt(
        session: OlmInboundGroupSessionPointer,
        message: ByteArray,
        plainText: ByteArray,
        messageIndex: MutableWrapper<UInt>
    ): ULong = memScoped {
        val messageIndexPtr = alloc<UIntVar>()
        olm_group_decrypt(
            session.ptr,
            message.asUByteArray().refTo(0), message.usize(),
            plainText.asUByteArray().refTo(0), plainText.usize(),
            messageIndexPtr.ptr
        ).also { messageIndex.value = messageIndexPtr.value }
    }

    actual fun inbound_group_session_id_length(session: OlmInboundGroupSessionPointer): ULong =
        olm_inbound_group_session_id_length(session.ptr)

    actual fun inbound_group_session_id(
        session: OlmInboundGroupSessionPointer,
        sessionId: ByteArray,
    ): ULong = olm_inbound_group_session_id(session.ptr, sessionId.asUByteArray().refTo(0), sessionId.usize())

    actual fun inbound_group_session_first_known_index(session: OlmInboundGroupSessionPointer): UInt =
        olm_inbound_group_session_first_known_index(session.ptr)

    actual fun inbound_group_session_is_verified(session: OlmInboundGroupSessionPointer): Int =
        olm_inbound_group_session_is_verified(session.ptr)

    actual fun export_inbound_group_session_length(session: OlmInboundGroupSessionPointer): ULong =
        olm_export_inbound_group_session_length(session.ptr)

    actual fun export_inbound_group_session(
        session: OlmInboundGroupSessionPointer,
        key: ByteArray,
        messageIndex: UInt
    ): ULong = olm_export_inbound_group_session(
        session.ptr,
        key.asUByteArray().refTo(0), key.usize(),
        messageIndex.convert()
    )

    actual fun outbound_group_session(): OlmOutboundGroupSessionPointer =
        OlmOutboundGroupSessionPointer(genericInit(::olm_outbound_group_session, olm_outbound_group_session_size()))

    actual fun outbound_group_session_last_error(session: OlmOutboundGroupSessionPointer): String? =
        olm_outbound_group_session_last_error(session.ptr)?.toKString()

    actual fun clear_outbound_group_session(session: OlmOutboundGroupSessionPointer): ULong =
        olm_clear_outbound_group_session(session.ptr)

    actual fun pickle_outbound_group_session_length(session: OlmOutboundGroupSessionPointer): ULong =
        olm_pickle_outbound_group_session_length(session.ptr)

    actual fun pickle_outbound_group_session(
        session: OlmOutboundGroupSessionPointer,
        key: ByteArray,
        pickled: ByteArray,
    ): ULong = olm_pickle_outbound_group_session(
        session.ptr,
        key.refTo(0), key.usize(),
        pickled.refTo(0), pickled.usize()
    )

    actual fun unpickle_outbound_group_session(
        session: OlmOutboundGroupSessionPointer,
        key: ByteArray,
        pickled: ByteArray,
    ): ULong = olm_unpickle_outbound_group_session(
        session.ptr,
        key.refTo(0), key.usize(),
        pickled.refTo(0), pickled.usize()
    )

    actual fun init_outbound_group_session_random_length(session: OlmOutboundGroupSessionPointer): ULong =
        olm_init_outbound_group_session_random_length(session.ptr)

    actual fun init_outbound_group_session(
        session: OlmOutboundGroupSessionPointer,
        random: ByteArray?,
    ): ULong = olm_init_outbound_group_session(
        session.ptr,
        random?.asUByteArray()?.refTo(0), random?.usize() ?: 0u
    )

    actual fun group_encrypt_message_length(
        session: OlmOutboundGroupSessionPointer,
        plainTextLength: ULong
    ): ULong = olm_group_encrypt_message_length(session.ptr, plainTextLength)

    actual fun group_encrypt(
        session: OlmOutboundGroupSessionPointer,
        plainText: ByteArray,
        message: ByteArray,
    ): ULong = olm_group_encrypt(
        session.ptr,
        plainText.asUByteArray().refTo(0), plainText.usize(),
        message.asUByteArray().refTo(0), message.usize()
    )

    actual fun outbound_group_session_id_length(session: OlmOutboundGroupSessionPointer): ULong =
        olm_outbound_group_session_id_length(session.ptr)

    actual fun outbound_group_session_id(
        session: OlmOutboundGroupSessionPointer,
        sessionId: ByteArray,
    ): ULong = olm_outbound_group_session_id(session.ptr, sessionId.asUByteArray().refTo(0), sessionId.usize())

    actual fun outbound_group_session_message_index(session: OlmOutboundGroupSessionPointer): UInt =
        olm_outbound_group_session_message_index(session.ptr)

    actual fun outbound_group_session_key_length(session: OlmOutboundGroupSessionPointer): ULong =
        olm_outbound_group_session_key_length(session.ptr)

    actual fun outbound_group_session_key(
        session: OlmOutboundGroupSessionPointer,
        key: ByteArray,
    ): ULong = olm_outbound_group_session_key(session.ptr, key.asUByteArray().refTo(0), key.usize())

    actual fun get_library_version(
        major: MutableWrapper<UInt>,
        minor: MutableWrapper<UInt>,
        patch: MutableWrapper<UInt>
    ) = memScoped {
        val majorPtr = alloc<UByteVar>()
        val minorPtr = alloc<UByteVar>()
        val patchPtr = alloc<UByteVar>()

        olm_get_library_version(majorPtr.ptr, minorPtr.ptr, patchPtr.ptr)
        major.value = majorPtr.value.toUInt()
        minor.value = minorPtr.value.toUInt()
        patch.value = patchPtr.value.toUInt()
    }

    actual fun account(): OlmAccountPointer = OlmAccountPointer(genericInit(::olm_account, olm_account_size()))

    actual fun account_last_error(account: OlmAccountPointer): String? =
        olm_account_last_error(account.ptr)?.toKString()

    actual fun clear_account(account: OlmAccountPointer): ULong = olm_clear_account(account.ptr)

    actual fun pickle_account_length(account: OlmAccountPointer): ULong = olm_pickle_account_length(account.ptr)

    actual fun pickle_account(
        account: OlmAccountPointer,
        key: ByteArray,
        pickled: ByteArray,
    ): ULong =
        olm_pickle_account(account.ptr, key.refTo(0), key.usize(), pickled.refTo(0), pickled.usize())

    actual fun unpickle_account(
        account: OlmAccountPointer,
        key: ByteArray,
        pickled: ByteArray,
    ): ULong =
        olm_unpickle_account(account.ptr, key.refTo(0), key.usize(), pickled.refTo(0), pickled.usize())

    actual fun create_account_random_length(account: OlmAccountPointer): ULong =
        olm_create_account_random_length(account.ptr)

    actual fun create_account(
        account: OlmAccountPointer,
        random: ByteArray?,
    ): ULong = olm_create_account(account.ptr, random?.refTo(0), random?.usize() ?: 0u)

    actual fun account_identity_keys_length(account: OlmAccountPointer): ULong =
        olm_account_identity_keys_length(account.ptr)

    actual fun account_identity_keys(
        account: OlmAccountPointer,
        identityKeys: ByteArray,
    ): ULong = olm_account_identity_keys(account.ptr, identityKeys.refTo(0), identityKeys.usize())

    actual fun account_signature_length(account: OlmAccountPointer): ULong =
        olm_account_signature_length(account.ptr)

    actual fun account_sign(
        account: OlmAccountPointer,
        message: ByteArray,
        signature: ByteArray,
    ): ULong = olm_account_sign(
        account.ptr,
        message.refTo(0), message.usize(),
        signature.refTo(0), signature.usize()
    )

    actual fun account_one_time_keys_length(account: OlmAccountPointer): ULong =
        olm_account_one_time_keys_length(account.ptr)

    actual fun account_one_time_keys(
        account: OlmAccountPointer,
        oneTimeKeys: ByteArray,
    ): ULong =
        olm_account_one_time_keys(account.ptr, oneTimeKeys.refTo(0), oneTimeKeys.usize())

    actual fun account_mark_keys_as_published(account: OlmAccountPointer): ULong =
        olm_account_mark_keys_as_published(account.ptr)

    actual fun account_max_number_of_one_time_keys(account: OlmAccountPointer): ULong =
        olm_account_max_number_of_one_time_keys(account.ptr)

    actual fun account_generate_one_time_keys_random_length(
        account: OlmAccountPointer,
        numberOfKeys: ULong
    ): ULong = olm_account_generate_one_time_keys_random_length(account.ptr, numberOfKeys)

    actual fun account_generate_one_time_keys(
        account: OlmAccountPointer,
        numberOfKeys: ULong,
        random: ByteArray?,
    ): ULong = olm_account_generate_one_time_keys(
        account.ptr,
        numberOfKeys,
        random?.refTo(0), random?.usize() ?: 0u
    )

    actual fun account_generate_fallback_key_random_length(
        account: OlmAccountPointer
    ): ULong = olm_account_generate_fallback_key_random_length(account.ptr)

    actual fun account_generate_fallback_key(
        account: OlmAccountPointer,
        random: ByteArray?,
    ): ULong = olm_account_generate_fallback_key(account.ptr, random?.refTo(0), random?.usize() ?: 0u)

    actual fun account_fallback_key_length(
        account: OlmAccountPointer
    ): ULong = olm_account_fallback_key_length(account.ptr)

    actual fun account_fallback_key(
        account: OlmAccountPointer,
        fallbackKey: ByteArray,
    ): ULong = olm_account_fallback_key(account.ptr, fallbackKey.refTo(0), fallbackKey.usize())

    actual fun session(): OlmSessionPointer =
        OlmSessionPointer(genericInit(::olm_session, olm_session_size()))

    actual fun session_last_error(session: OlmSessionPointer): String? =
        olm_session_last_error(session.ptr)?.toKString()

    actual fun clear_session(session: OlmSessionPointer): ULong = olm_clear_session(session.ptr)

    actual fun pickle_session_length(session: OlmSessionPointer): ULong = olm_pickle_session_length(session.ptr)

    actual fun pickle_session(
        session: OlmSessionPointer,
        key: ByteArray,
        pickled: ByteArray,
    ): ULong = olm_pickle_session(
        session.ptr,
        key.refTo(0), key.usize(),
        pickled.refTo(0), pickled.usize()
    )

    actual fun unpickle_session(
        session: OlmSessionPointer,
        key: ByteArray,
        pickled: ByteArray,
    ): ULong = olm_unpickle_session(
        session.ptr,
        key.refTo(0), key.usize(),
        pickled.refTo(0), pickled.usize()
    )

    actual fun create_outbound_session_random_length(session: OlmSessionPointer): ULong =
        olm_create_outbound_session_random_length(session.ptr)

    actual fun create_outbound_session(
        session: OlmSessionPointer,
        account: OlmAccountPointer,
        theirIdentityKey: ByteArray,
        theirOneTimeKey: ByteArray,
        random: ByteArray?,
    ): ULong = olm_create_outbound_session(
        session.ptr, account.ptr,
        theirIdentityKey.refTo(0), theirIdentityKey.usize(),
        theirOneTimeKey.refTo(0), theirOneTimeKey.usize(),
        random?.refTo(0), random?.usize() ?: 0u
    )

    actual fun create_inbound_session(
        session: OlmSessionPointer,
        account: OlmAccountPointer,
        oneTimeKeyMessage: ByteArray,
    ): ULong = olm_create_inbound_session(
        session.ptr,
        account.ptr,
        oneTimeKeyMessage.refTo(0), oneTimeKeyMessage.usize()
    )

    actual fun create_inbound_session_from(
        session: OlmSessionPointer,
        account: OlmAccountPointer,
        theirIdentityKey: ByteArray,
        oneTimeKeyMessage: ByteArray,
    ): ULong = olm_create_inbound_session_from(
        session.ptr,
        account.ptr,
        theirIdentityKey.refTo(0), theirIdentityKey.usize(),
        oneTimeKeyMessage.refTo(0), oneTimeKeyMessage.usize()
    )

    actual fun session_id_length(session: OlmSessionPointer): ULong = olm_session_id_length(session.ptr)

    actual fun session_id(
        session: OlmSessionPointer,
        id: ByteArray,
    ): ULong = olm_session_id(session.ptr, id.refTo(0), id.usize())

    actual fun session_has_received_message(session: OlmSessionPointer): Int =
        olm_session_has_received_message(session.ptr)

    actual fun session_describe(
        session: OlmSessionPointer,
        description: ByteArray,
    ) = olm_session_describe(session.ptr, description.refTo(0), description.usize())

    actual fun matches_inbound_session(
        session: OlmSessionPointer,
        oneTimeKeyMessage: ByteArray,
    ): ULong = olm_matches_inbound_session(session.ptr, oneTimeKeyMessage.refTo(0), oneTimeKeyMessage.usize())

    actual fun matches_inbound_session_from(
        session: OlmSessionPointer,
        theirIdentityKey: ByteArray,
        oneTimeKeyMessage: ByteArray,
    ): ULong = olm_matches_inbound_session_from(
        session.ptr,
        theirIdentityKey.refTo(0), theirIdentityKey.usize(),
        oneTimeKeyMessage.refTo(0), oneTimeKeyMessage.usize()
    )

    actual fun remove_one_time_keys(account: OlmAccountPointer, session: OlmSessionPointer): ULong =
        olm_remove_one_time_keys(account.ptr, session.ptr)

    actual fun encrypt_message_type(session: OlmSessionPointer): ULong = olm_encrypt_message_type(session.ptr)

    actual fun encrypt_random_length(session: OlmSessionPointer): ULong = olm_encrypt_random_length(session.ptr)

    actual fun encrypt_message_length(session: OlmSessionPointer, plainTextLength: ULong): ULong =
        olm_encrypt_message_length(session.ptr, plainTextLength)

    actual fun encrypt(
        session: OlmSessionPointer,
        plainText: ByteArray,
        random: ByteArray?,
        message: ByteArray,
    ): ULong = olm_encrypt(
        session.ptr,
        plainText.refTo(0), plainText.usize(),
        random?.refTo(0), random?.usize() ?: 0u,
        message.refTo(0), message.usize()
    )

    actual fun decrypt_max_plaintext_length(
        session: OlmSessionPointer,
        messageType: ULong,
        message: ByteArray,
    ): ULong = olm_decrypt_max_plaintext_length(session.ptr, messageType, message.refTo(0), message.usize())

    actual fun decrypt(
        session: OlmSessionPointer,
        messageType: ULong,
        message: ByteArray,
        plainText: ByteArray,
    ): ULong = olm_decrypt(
        session.ptr,
        messageType,
        message.refTo(0), message.usize(),
        plainText.refTo(0), plainText.usize()
    )

    actual fun utility(): OlmUtilityPointer = OlmUtilityPointer(genericInit(::olm_utility, olm_utility_size()))

    actual fun utility_last_error(utility: OlmUtilityPointer): String? =
        olm_utility_last_error(utility.ptr)?.toKString()

    actual fun clear_utility(utility: OlmUtilityPointer): ULong = olm_clear_utility(utility.ptr)

    actual fun sha256_length(utility: OlmUtilityPointer): ULong = olm_sha256_length(utility.ptr)

    actual fun sha256(
        utility: OlmUtilityPointer,
        input: ByteArray,
        output: ByteArray,
    ): ULong = olm_sha256(utility.ptr, input.refTo(0), input.usize(), output.refTo(0), output.usize())

    actual fun ed25519_verify(
        utility: OlmUtilityPointer,
        key: ByteArray,
        message: ByteArray,
        signature: ByteArray,
    ): ULong = olm_ed25519_verify(
        utility.ptr,
        key.refTo(0), key.usize(),
        message.refTo(0), message.usize(),
        signature.refTo(0), signature.usize()
    )

    actual fun sas(): OlmSASPointer = OlmSASPointer(genericInit(::olm_sas, olm_sas_size()))

    actual fun sas_last_error(sas: OlmSASPointer): String? = olm_sas_last_error(sas.ptr)?.toKString()

    actual fun clear_sas(sas: OlmSASPointer): ULong = olm_clear_sas(sas.ptr)

    actual fun create_sas_random_length(sas: OlmSASPointer): ULong = olm_create_sas_random_length(sas.ptr)

    actual fun create_sas(sas: OlmSASPointer, random: ByteArray?): ULong =
        olm_create_sas(sas.ptr, random?.refTo(0), random?.usize() ?: 0u)

    actual fun sas_pubkey_length(sas: OlmSASPointer): ULong = olm_sas_pubkey_length(sas.ptr)

    actual fun sas_get_pubkey(sas: OlmSASPointer, pubkey: ByteArray): ULong =
        olm_sas_get_pubkey(sas.ptr, pubkey.refTo(0), pubkey.usize())

    actual fun sas_set_their_key(
        sas: OlmSASPointer,
        theirKey: ByteArray
    ): ULong = olm_sas_set_their_key(sas.ptr, theirKey.refTo(0), theirKey.usize())

    actual fun sas_is_their_key_set(sas: OlmSASPointer): Int = olm_sas_is_their_key_set(sas.ptr)

    actual fun sas_generate_bytes(
        sas: OlmSASPointer,
        info: ByteArray,
        output: ByteArray
    ): ULong =
        olm_sas_generate_bytes(sas.ptr, info.refTo(0), info.usize(), output.refTo(0), output.usize())

    actual fun sas_mac_length(sas: OlmSASPointer): ULong = olm_sas_mac_length(sas.ptr)

    actual fun sas_calculate_mac(
        sas: OlmSASPointer,
        input: ByteArray,
        info: ByteArray,
        mac: ByteArray
    ): ULong = olm_sas_calculate_mac(
        sas.ptr,
        input.refTo(0), input.usize(),
        info.refTo(0), info.usize(),
        mac.refTo(0), mac.usize()
    )

    actual fun sas_calculate_mac_ULong_kdf(
        sas: OlmSASPointer,
        input: ByteArray,
        info: ByteArray,
        mac: ByteArray
    ): ULong = olm_sas_calculate_mac(
        sas.ptr,
        input.refTo(0), input.usize(),
        info.refTo(0), info.usize(),
        mac.refTo(0), mac.usize()
    )

    actual fun pk_encryption(): OlmPkEncryptionPointer =
        OlmPkEncryptionPointer(genericInit(::olm_pk_encryption, olm_pk_encryption_size()))

    actual fun pk_encryption_last_error(encryption: OlmPkEncryptionPointer): String? =
        olm_pk_encryption_last_error(encryption.ptr)?.toKString()

    actual fun clear_pk_encryption(encryption: OlmPkEncryptionPointer): ULong = olm_clear_pk_encryption(encryption.ptr)

    actual fun pk_encryption_set_recipient_key(
        encryption: OlmPkEncryptionPointer,
        publicKey: ByteArray
    ): ULong = olm_pk_encryption_set_recipient_key(encryption.ptr, publicKey.refTo(0), publicKey.usize())

    actual fun pk_ciphertext_length(
        encryption: OlmPkEncryptionPointer,
        plainTextLength: ULong
    ): ULong = olm_pk_ciphertext_length(encryption.ptr, plainTextLength)

    actual fun pk_mac_length(encryption: OlmPkEncryptionPointer): ULong = olm_pk_mac_length(encryption.ptr)

    actual fun pk_key_length(): ULong = olm_pk_key_length()

    actual fun pk_encrypt_random_length(encryption: OlmPkEncryptionPointer): ULong =
        olm_pk_encrypt_random_length(encryption.ptr)

    actual fun pk_encrypt(
        encryption: OlmPkEncryptionPointer,
        plainText: ByteArray,
        cipherText: ByteArray,
        mac: ByteArray,
        ephemeralKey: ByteArray,
        random: ByteArray?
    ): ULong = olm_pk_encrypt(
        encryption.ptr,
        plainText.refTo(0), plainText.usize(),
        cipherText.refTo(0), cipherText.usize(),
        mac.refTo(0), mac.usize(),
        ephemeralKey.refTo(0), ephemeralKey.usize(),
        random?.refTo(0), random?.usize() ?: 0u
    )

    actual fun pk_decryption(): OlmPkDecryptionPointer =
        OlmPkDecryptionPointer(genericInit(::olm_pk_decryption, olm_pk_decryption_size()))


    actual fun pk_decryption_last_error(decryption: OlmPkDecryptionPointer): String? =
        olm_pk_decryption_last_error(decryption.ptr)?.toKString()

    actual fun clear_pk_decryption(decryption: OlmPkDecryptionPointer): ULong = olm_clear_pk_decryption(decryption.ptr)

    actual fun pk_private_key_length(): ULong = olm_pk_private_key_length()

    actual fun pk_key_from_private(
        decryption: OlmPkDecryptionPointer,
        pubkey: ByteArray,
        privkey: ByteArray
    ): ULong =
        olm_pk_key_from_private(decryption.ptr, pubkey.refTo(0), pubkey.usize(), privkey.refTo(0), privkey.usize())

    actual fun pickle_pk_decryption_length(decryption: OlmPkDecryptionPointer): ULong =
        olm_pickle_pk_decryption_length(decryption.ptr)

    actual fun pickle_pk_decryption(
        decryption: OlmPkDecryptionPointer,
        key: ByteArray,
        pickled: ByteArray
    ): ULong = olm_pickle_pk_decryption(decryption.ptr, key.refTo(0), key.usize(), pickled.refTo(0), pickled.usize())

    actual fun unpickle_pk_decryption(
        decryption: OlmPkDecryptionPointer,
        key: ByteArray,
        pickled: ByteArray,
        pubkey: ByteArray
    ): ULong = olm_unpickle_pk_decryption(
        decryption.ptr,
        key.refTo(0), key.usize(),
        pickled.refTo(0), pickled.usize(),
        pubkey.refTo(0), pubkey.usize()
    )

    actual fun pk_max_plaintext_length(
        decryption: OlmPkDecryptionPointer,
        cipherTextLength: ULong
    ): ULong = olm_pk_max_plaintext_length(decryption.ptr, cipherTextLength)

    actual fun pk_decrypt(
        decryption: OlmPkDecryptionPointer,
        ephemeralKey: ByteArray,
        mac: ByteArray,
        cipherText: ByteArray,
        plainText: ByteArray
    ): ULong = olm_pk_decrypt(
        decryption.ptr,
        ephemeralKey.refTo(0), ephemeralKey.usize(),
        mac.refTo(0), mac.usize(),
        cipherText.refTo(0), cipherText.usize(),
        plainText.refTo(0), plainText.usize(),
    )

    actual fun pk_get_private_key(
        decryption: OlmPkDecryptionPointer,
        privateKey: ByteArray
    ): ULong = olm_pk_get_private_key(decryption.ptr, privateKey.refTo(0), privateKey.usize())

    actual fun pk_signing(): OlmPkSigningPointer =
        OlmPkSigningPointer(genericInit(::olm_pk_signing, olm_pk_signing_size()))

    actual fun pk_signing_last_error(sign: OlmPkSigningPointer): String? =
        olm_pk_signing_last_error(sign.ptr)?.toKString()

    actual fun clear_pk_signing(sign: OlmPkSigningPointer): ULong = olm_clear_pk_signing(sign.ptr)

    actual fun pk_signing_key_from_seed(
        sign: OlmPkSigningPointer,
        pubkey: ByteArray,
        seed: ByteArray
    ): ULong = olm_pk_signing_key_from_seed(sign.ptr, pubkey.refTo(0), pubkey.usize(), seed.refTo(0), seed.usize())

    actual fun pk_signing_seed_length(): ULong = olm_pk_signing_seed_length()

    actual fun pk_signing_public_key_length(): ULong = olm_pk_signing_public_key_length()

    actual fun pk_signature_length(): ULong = olm_pk_signature_length()

    actual fun pk_sign(
        sign: OlmPkSigningPointer,
        message: ByteArray,
        signature: ByteArray
    ): ULong = olm_pk_sign(
        sign.ptr,
        message.asUByteArray().refTo(0), message.usize(),
        signature.asUByteArray().refTo(0), signature.usize()
    )

}