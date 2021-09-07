@file:Suppress("FunctionName")

package net.folivo.trixnity.olm

import com.sun.jna.ptr.IntByReference
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_account
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_account_fallback_key
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_account_fallback_key_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_account_generate_fallback_key
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_account_generate_fallback_key_random_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_account_generate_one_time_keys
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_account_generate_one_time_keys_random_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_account_identity_keys
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_account_identity_keys_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_account_last_error
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_account_mark_keys_as_published
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_account_max_number_of_one_time_keys
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_account_one_time_keys
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_account_one_time_keys_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_account_sign
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_account_signature_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_account_size
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_clear_account
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_clear_inbound_group_session
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_clear_outbound_group_session
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_clear_pk_decryption
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_clear_pk_encryption
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_clear_pk_signing
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_clear_sas
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_clear_session
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_clear_utility
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_create_account
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_create_account_random_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_create_inbound_session
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_create_inbound_session_from
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_create_outbound_session
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_create_outbound_session_random_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_create_sas
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_create_sas_random_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_decrypt
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_decrypt_max_plaintext_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_ed25519_verify
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_encrypt
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_encrypt_message_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_encrypt_message_type
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_encrypt_random_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_error
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_export_inbound_group_session
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_export_inbound_group_session_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_get_library_version
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_group_decrypt
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_group_decrypt_max_plaintext_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_group_encrypt
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_group_encrypt_message_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_import_inbound_group_session
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_inbound_group_session
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_inbound_group_session_first_known_index
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_inbound_group_session_id
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_inbound_group_session_id_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_inbound_group_session_is_verified
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_inbound_group_session_last_error
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_inbound_group_session_size
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_init_inbound_group_session
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_init_outbound_group_session
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_init_outbound_group_session_random_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_matches_inbound_session
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_matches_inbound_session_from
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_outbound_group_session
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_outbound_group_session_id
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_outbound_group_session_id_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_outbound_group_session_key
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_outbound_group_session_key_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_outbound_group_session_last_error
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_outbound_group_session_message_index
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_outbound_group_session_size
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pickle_account
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pickle_account_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pickle_inbound_group_session
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pickle_inbound_group_session_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pickle_outbound_group_session
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pickle_outbound_group_session_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pickle_pk_decryption
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pickle_pk_decryption_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pickle_session
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pickle_session_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_ciphertext_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_decrypt
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_decryption
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_decryption_last_error
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_decryption_size
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_encrypt
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_encrypt_random_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_encryption
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_encryption_last_error
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_encryption_set_recipient_key
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_encryption_size
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_get_private_key
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_key_from_private
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_key_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_mac_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_max_plaintext_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_private_key_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_sign
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_signature_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_signing
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_signing_key_from_seed
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_signing_last_error
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_signing_public_key_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_signing_seed_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_pk_signing_size
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_remove_one_time_keys
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_sas
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_sas_calculate_mac
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_sas_calculate_mac_long_kdf
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_sas_generate_bytes
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_sas_get_pubkey
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_sas_is_their_key_set
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_sas_last_error
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_sas_mac_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_sas_pubkey_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_sas_set_their_key
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_sas_size
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_session
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_session_describe
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_session_has_received_message
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_session_id
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_session_id_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_session_last_error
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_session_size
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_sha256
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_sha256_length
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_unpickle_account
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_unpickle_inbound_group_session
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_unpickle_outbound_group_session
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_unpickle_pk_decryption
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_unpickle_session
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_utility
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_utility_last_error
import net.folivo.trixnity.olm.OlmLibraryWrapper.olm_utility_size

actual object OlmLibrary {

    actual fun error(): ULong = olm_error().toULong()

    actual fun inbound_group_session(): OlmInboundGroupSessionPointer =
        genericInit(::olm_inbound_group_session, olm_inbound_group_session_size())

    actual fun inbound_group_session_last_error(session: OlmInboundGroupSessionPointer): String? =
        olm_inbound_group_session_last_error(session)

    actual fun clear_inbound_group_session(session: OlmInboundGroupSessionPointer) =
        olm_clear_inbound_group_session(session).toULong()

    actual fun pickle_inbound_group_session_length(session: OlmInboundGroupSessionPointer): ULong =
        olm_pickle_inbound_group_session_length(session).toULong()

    actual fun pickle_inbound_group_session(
        session: OlmInboundGroupSessionPointer,
        key: ByteArray,
        pickled: ByteArray
    ): ULong = key.withNativeRead { keyPtr, keySize ->
        pickled.withNativeWrite { pickledPtr, pickledSize ->
            olm_pickle_inbound_group_session(session, keyPtr, keySize, pickledPtr, pickledSize).toULong()
        }
    }

    actual fun unpickle_inbound_group_session(
        session: OlmInboundGroupSessionPointer,
        key: ByteArray,
        pickled: ByteArray,
    ): ULong = key.withNativeRead { keyPtr, keySize ->
        pickled.withNativeRead { pickledPtr, pickledSize ->
            olm_unpickle_inbound_group_session(session, keyPtr, keySize, pickledPtr, pickledSize).toULong()
        }
    }

    actual fun init_inbound_group_session(
        session: OlmInboundGroupSessionPointer,
        key: ByteArray,
    ): ULong = key.withNativeRead { sessionKeyPtr, sessionKeySize ->
        olm_init_inbound_group_session(session, sessionKeyPtr, sessionKeySize).toULong()
    }

    actual fun import_inbound_group_session(
        session: OlmInboundGroupSessionPointer,
        key: ByteArray,
    ): ULong = key.withNativeRead { sessionKeyPtr, sessionSize ->
        olm_import_inbound_group_session(session, sessionKeyPtr, sessionSize).toULong()
    }

    actual fun group_decrypt_max_plaintext_length(
        session: OlmInboundGroupSessionPointer,
        message: ByteArray,
    ): ULong = message.withNativeRead { messagePtr, messageSize ->
        olm_group_decrypt_max_plaintext_length(session, messagePtr, messageSize).toULong()
    }

    actual fun group_decrypt(
        session: OlmInboundGroupSessionPointer,
        message: ByteArray,
        plainText: ByteArray,
        messageIndex: MutableWrapper<UInt>
    ): ULong = message.withNativeRead { messagePtr, messageSize ->
        plainText.withNativeWrite { plainTextPtr, plainTextSize ->
            val messageIndexPtr = IntByReference()
            olm_group_decrypt(session, messagePtr, messageSize, plainTextPtr, plainTextSize, messageIndexPtr).toULong()
                .also { messageIndex.value = messageIndexPtr.value.toUInt() }
        }
    }

    actual fun inbound_group_session_id_length(session: OlmInboundGroupSessionPointer): ULong =
        olm_inbound_group_session_id_length(session).toULong()

    actual fun inbound_group_session_id(
        session: OlmInboundGroupSessionPointer,
        sessionId: ByteArray,
    ): ULong = sessionId.withNativeWrite { sessionIdPtr, sessionSize ->
        olm_inbound_group_session_id(session, sessionIdPtr, sessionSize).toULong()
    }

    actual fun inbound_group_session_first_known_index(session: OlmInboundGroupSessionPointer): UInt =
        olm_inbound_group_session_first_known_index(session).toUInt()

    actual fun inbound_group_session_is_verified(session: OlmInboundGroupSessionPointer): Int =
        olm_inbound_group_session_is_verified(session)

    actual fun export_inbound_group_session_length(session: OlmInboundGroupSessionPointer): ULong =
        olm_export_inbound_group_session_length(session).toULong()

    actual fun export_inbound_group_session(
        session: OlmInboundGroupSessionPointer,
        key: ByteArray,
        messageIndex: UInt
    ): ULong = key.withNativeWrite { keyPtr, keySize ->
        olm_export_inbound_group_session(session, keyPtr, keySize, messageIndex.toInt()).toULong()
    }

    actual fun outbound_group_session(): OlmOutboundGroupSessionPointer =
        genericInit(::olm_outbound_group_session, olm_outbound_group_session_size())

    actual fun outbound_group_session_last_error(session: OlmOutboundGroupSessionPointer): String? =
        olm_outbound_group_session_last_error(session)

    actual fun clear_outbound_group_session(session: OlmOutboundGroupSessionPointer): ULong =
        olm_clear_outbound_group_session(session).toULong()

    actual fun pickle_outbound_group_session_length(session: OlmOutboundGroupSessionPointer): ULong =
        olm_pickle_outbound_group_session_length(session).toULong()

    actual fun pickle_outbound_group_session(
        session: OlmOutboundGroupSessionPointer,
        key: ByteArray,
        pickled: ByteArray
    ): ULong = key.withNativeRead { keyPtr, keySize ->
        pickled.withNativeWrite { pickledPtr, pickledSize ->
            olm_pickle_outbound_group_session(session, keyPtr, keySize, pickledPtr, pickledSize).toULong()
        }
    }

    actual fun unpickle_outbound_group_session(
        session: OlmOutboundGroupSessionPointer,
        key: ByteArray,
        pickled: ByteArray,
    ): ULong = key.withNativeRead { keyPtr, keySize ->
        pickled.withNativeRead { pickledPtr, pickledSize ->
            olm_unpickle_outbound_group_session(session, keyPtr, keySize, pickledPtr, pickledSize).toULong()
        }
    }

    actual fun init_outbound_group_session_random_length(session: OlmOutboundGroupSessionPointer): ULong =
        olm_init_outbound_group_session_random_length(session).toULong()

    actual fun init_outbound_group_session(
        session: OlmOutboundGroupSessionPointer,
        random: ByteArray?,
    ): ULong = random.withNativeRead { randomPtr, randomSize ->
        olm_init_outbound_group_session(session, randomPtr, randomSize).toULong()
    }

    actual fun group_encrypt_message_length(
        session: OlmOutboundGroupSessionPointer,
        plainTextLength: ULong
    ): ULong = olm_group_encrypt_message_length(session, NativeSize(plainTextLength)).toULong()

    actual fun group_encrypt(
        session: OlmOutboundGroupSessionPointer,
        plainText: ByteArray,
        message: ByteArray,
    ): ULong = plainText.withNativeRead { plainTextPtr, plainTextSize ->
        message.withNativeWrite { messagePtr, messageSize ->
            olm_group_encrypt(session, plainTextPtr, plainTextSize, messagePtr, messageSize).toULong()
        }
    }

    actual fun outbound_group_session_id_length(session: OlmOutboundGroupSessionPointer): ULong =
        olm_outbound_group_session_id_length(session).toULong()

    actual fun outbound_group_session_id(
        session: OlmOutboundGroupSessionPointer,
        sessionId: ByteArray,
    ): ULong = sessionId.withNativeWrite { sessionIdPtr, sessionSize ->
        olm_outbound_group_session_id(session, sessionIdPtr, sessionSize).toULong()
    }

    actual fun outbound_group_session_message_index(session: OlmOutboundGroupSessionPointer): UInt =
        olm_outbound_group_session_message_index(session).toUInt()

    actual fun outbound_group_session_key_length(session: OlmOutboundGroupSessionPointer): ULong =
        olm_outbound_group_session_key_length(session).toULong()

    actual fun outbound_group_session_key(
        session: OlmOutboundGroupSessionPointer,
        key: ByteArray,
    ): ULong = key.withNativeWrite { keyPtr, keySize ->
        olm_outbound_group_session_key(session, keyPtr, keySize).toULong()
    }

    actual fun get_library_version(
        major: MutableWrapper<UInt>,
        minor: MutableWrapper<UInt>,
        patch: MutableWrapper<UInt>
    ) {
        val majorPtr = IntByReference()
        val minorPtr = IntByReference()
        val patchPtr = IntByReference()
        olm_get_library_version(majorPtr, minorPtr, patchPtr)
        major.value = majorPtr.value.toUInt()
        minor.value = minorPtr.value.toUInt()
        patch.value = patchPtr.value.toUInt()
    }

    actual fun account(): OlmAccountPointer = genericInit(::olm_account, olm_account_size())

    actual fun account_last_error(account: OlmAccountPointer): String? = olm_account_last_error(account)

    actual fun clear_account(account: OlmAccountPointer): ULong = olm_clear_account(account).toULong()

    actual fun pickle_account_length(account: OlmAccountPointer): ULong = olm_pickle_account_length(account).toULong()

    actual fun pickle_account(
        account: OlmAccountPointer,
        key: ByteArray,
        pickled: ByteArray,
    ): ULong = key.withNativeRead { keyPtr, keySize ->
        pickled.withNativeWrite { pickledPtr, pickledSize ->
            olm_pickle_account(account, keyPtr, keySize, pickledPtr, pickledSize).toULong()
        }
    }

    actual fun unpickle_account(
        account: OlmAccountPointer,
        key: ByteArray,
        pickled: ByteArray,
    ): ULong = key.withNativeRead { keyPtr, keySize ->
        pickled.withNativeRead { pickledPtr, pickledSize ->
            olm_unpickle_account(account, keyPtr, keySize, pickledPtr, pickledSize).toULong()
        }
    }

    actual fun create_account_random_length(account: OlmAccountPointer): ULong =
        olm_create_account_random_length(account).toULong()

    actual fun create_account(
        account: OlmAccountPointer,
        random: ByteArray?,
    ): ULong = random.withNativeRead { randomPtr, randomSize ->
        olm_create_account(account, randomPtr, randomSize).toULong()
    }

    actual fun account_identity_keys_length(account: OlmAccountPointer): ULong =
        olm_account_identity_keys_length(account).toULong()

    actual fun account_identity_keys(
        account: OlmAccountPointer,
        identityKeys: ByteArray,
    ): ULong = identityKeys.withNativeWrite { identityKeysPtr, identityKeysSize ->
        olm_account_identity_keys(account, identityKeysPtr, identityKeysSize).toULong()
    }

    actual fun account_signature_length(account: OlmAccountPointer): ULong =
        olm_account_signature_length(account).toULong()

    actual fun account_sign(
        account: OlmAccountPointer,
        message: ByteArray,
        signature: ByteArray,
    ): ULong = message.withNativeRead { messagePtr, messageSize ->
        signature.withNativeWrite { signaturePtr, signatureSize ->
            olm_account_sign(account, messagePtr, messageSize, signaturePtr, signatureSize).toULong()
        }
    }

    actual fun account_one_time_keys_length(account: OlmAccountPointer): ULong =
        olm_account_one_time_keys_length(account).toULong()

    actual fun account_one_time_keys(
        account: OlmAccountPointer,
        oneTimeKeys: ByteArray,
    ): ULong = oneTimeKeys.withNativeWrite { oneTimeKeysPtr, oneTimeKeysSize ->
        olm_account_one_time_keys(account, oneTimeKeysPtr, oneTimeKeysSize).toULong()
    }

    actual fun account_mark_keys_as_published(account: OlmAccountPointer): ULong =
        olm_account_mark_keys_as_published(account).toULong()

    actual fun account_max_number_of_one_time_keys(account: OlmAccountPointer): ULong =
        olm_account_max_number_of_one_time_keys(account).toULong()

    actual fun account_generate_one_time_keys_random_length(
        account: OlmAccountPointer,
        numberOfKeys: ULong
    ): ULong = olm_account_generate_one_time_keys_random_length(account, NativeSize(numberOfKeys)).toULong()

    actual fun account_generate_one_time_keys(
        account: OlmAccountPointer,
        numberOfKeys: ULong,
        random: ByteArray?,
    ): ULong = random.withNativeRead { randomPtr, randomSize ->
        olm_account_generate_one_time_keys(account, NativeSize(numberOfKeys), randomPtr, randomSize).toULong()
    }

    actual fun account_generate_fallback_key_random_length(
        account: OlmAccountPointer
    ): ULong = olm_account_generate_fallback_key_random_length(account).toULong()

    actual fun account_generate_fallback_key(
        account: OlmAccountPointer,
        random: ByteArray?,
    ): ULong = random.withNativeRead { randomPtr, randomSize ->
        olm_account_generate_fallback_key(account, randomPtr, randomSize).toULong()
    }

    actual fun account_fallback_key_length(
        account: OlmAccountPointer
    ): ULong = olm_account_fallback_key_length(account).toULong()

    actual fun account_fallback_key(
        account: OlmAccountPointer,
        fallbackKey: ByteArray,
    ): ULong = fallbackKey.withNativeWrite { fallbackKeyPtr, fallbackKeySize ->
        olm_account_fallback_key(account, fallbackKeyPtr, fallbackKeySize).toULong()
    }

    actual fun session(): OlmSessionPointer = genericInit(::olm_session, olm_session_size())

    actual fun session_last_error(session: OlmSessionPointer): String? = olm_session_last_error(session)

    actual fun clear_session(session: OlmSessionPointer): ULong = olm_clear_session(session).toULong()

    actual fun pickle_session_length(session: OlmSessionPointer): ULong = olm_pickle_session_length(session).toULong()

    actual fun pickle_session(
        session: OlmSessionPointer,
        key: ByteArray,
        pickled: ByteArray,
    ): ULong = key.withNativeRead { keyPtr, keySize ->
        pickled.withNativeWrite { pickledPtr, pickledSize ->
            olm_pickle_session(session, keyPtr, keySize, pickledPtr, pickledSize).toULong()
        }
    }

    actual fun unpickle_session(
        session: OlmSessionPointer,
        key: ByteArray,
        pickled: ByteArray,
    ): ULong = key.withNativeRead { keyPtr, keySize ->
        pickled.withNativeRead { pickledPtr, pickledSize ->
            olm_unpickle_session(session, keyPtr, keySize, pickledPtr, pickledSize).toULong()
        }
    }

    actual fun create_outbound_session_random_length(session: OlmSessionPointer): ULong =
        olm_create_outbound_session_random_length(session).toULong()

    actual fun create_outbound_session(
        session: OlmSessionPointer,
        account: OlmAccountPointer,
        theirIdentityKey: ByteArray,
        theirOneTimeKey: ByteArray,
        random: ByteArray?,
    ): ULong = theirIdentityKey.withNativeRead { theirIdentityKeyPtr, theirIdentityKeySize ->
        theirOneTimeKey.withNativeRead { theirOneTimeKeyPtr, theirOneTimeKeySize ->
            random.withNativeRead { randomPtr, randomSize ->
                olm_create_outbound_session(
                    session,
                    account,
                    theirIdentityKeyPtr, theirIdentityKeySize,
                    theirOneTimeKeyPtr, theirOneTimeKeySize,
                    randomPtr, randomSize
                ).toULong()
            }
        }
    }

    actual fun create_inbound_session(
        session: OlmSessionPointer,
        account: OlmAccountPointer,
        oneTimeKeyMessage: ByteArray,
    ): ULong = oneTimeKeyMessage.withNativeRead { oneTimeKeyMessagePtr, oneTimeKeyMessageSize ->
        olm_create_inbound_session(session, account, oneTimeKeyMessagePtr, oneTimeKeyMessageSize).toULong()
    }

    actual fun create_inbound_session_from(
        session: OlmSessionPointer,
        account: OlmAccountPointer,
        theirIdentityKey: ByteArray,
        oneTimeKeyMessage: ByteArray,
    ): ULong = theirIdentityKey.withNativeRead { theirIdentityKeyPtr, theirIdentityKeySize ->
        oneTimeKeyMessage.withNativeRead { oneTimeKeyMessagePtr, oneTimeKeyMessageSize ->
            olm_create_inbound_session_from(
                session,
                account,
                theirIdentityKeyPtr, theirIdentityKeySize,
                oneTimeKeyMessagePtr, oneTimeKeyMessageSize
            ).toULong()
        }
    }

    actual fun session_id_length(session: OlmSessionPointer): ULong = olm_session_id_length(session).toULong()

    actual fun session_id(
        session: OlmSessionPointer,
        id: ByteArray,
    ): ULong = id.withNativeWrite { idPtr, idSize ->
        olm_session_id(session, idPtr, idSize).toULong()
    }

    actual fun session_has_received_message(session: OlmSessionPointer): Int = olm_session_has_received_message(session)

    actual fun session_describe(
        session: OlmSessionPointer,
        description: ByteArray,
    ) = description.withNativeWrite { descriptionPtr, descriptionSize ->
        olm_session_describe(session, descriptionPtr, descriptionSize)
    }

    actual fun matches_inbound_session(
        session: OlmSessionPointer,
        oneTimeKeyMessage: ByteArray,
    ): ULong = oneTimeKeyMessage.withNativeRead { oneTimeKeyMessagePtr, oneTimeKeyMessageSize ->
        olm_matches_inbound_session(session, oneTimeKeyMessagePtr, oneTimeKeyMessageSize).toULong()
    }

    actual fun matches_inbound_session_from(
        session: OlmSessionPointer,
        theirIdentityKey: ByteArray,
        oneTimeKeyMessage: ByteArray,
    ): ULong = theirIdentityKey.withNativeRead { theirIdentityKeyPtr, theirIdentityKeySize ->
        oneTimeKeyMessage.withNativeRead { oneTimeKeyMessagePtr, oneTimeKeyMessageSize ->
            olm_matches_inbound_session_from(
                session,
                theirIdentityKeyPtr, theirIdentityKeySize,
                oneTimeKeyMessagePtr, oneTimeKeyMessageSize
            ).toULong()
        }
    }

    actual fun remove_one_time_keys(account: OlmAccountPointer, session: OlmSessionPointer): ULong =
        olm_remove_one_time_keys(account, session).toULong()

    actual fun encrypt_message_type(session: OlmSessionPointer): ULong = olm_encrypt_message_type(session).toULong()

    actual fun encrypt_random_length(session: OlmSessionPointer): ULong = olm_encrypt_random_length(session).toULong()

    actual fun encrypt_message_length(session: OlmSessionPointer, plainTextLength: ULong): ULong =
        olm_encrypt_message_length(session, NativeSize(plainTextLength)).toULong()

    actual fun encrypt(
        session: OlmSessionPointer,
        plainText: ByteArray,
        random: ByteArray?,
        message: ByteArray,
    ): ULong = plainText.withNativeRead { plainTextPtr, plainTextSize ->
        random.withNativeRead { randomPtr, randomSize ->
            message.withNativeWrite { messagePtr, messageSize ->
                olm_encrypt(
                    session,
                    plainTextPtr, plainTextSize,
                    randomPtr, randomSize,
                    messagePtr, messageSize
                ).toULong()
            }
        }
    }

    actual fun decrypt_max_plaintext_length(
        session: OlmSessionPointer,
        messageType: ULong,
        message: ByteArray,
    ): ULong = message.withNativeRead { messagePtr, messageSize ->
        olm_decrypt_max_plaintext_length(session, NativeSize(messageType), messagePtr, messageSize).toULong()
    }

    actual fun decrypt(
        session: OlmSessionPointer,
        messageType: ULong,
        message: ByteArray,
        plainText: ByteArray,
    ): ULong = message.withNativeRead { messagePtr, messageSize ->
        plainText.withNativeWrite { plainTextPtr, plainTextSize ->
            olm_decrypt(
                session,
                NativeSize(messageType),
                messagePtr, messageSize,
                plainTextPtr, plainTextSize
            ).toULong()
        }
    }

    actual fun utility(): OlmUtilityPointer = genericInit(::olm_utility, olm_utility_size())

    actual fun utility_last_error(utility: OlmUtilityPointer): String? = olm_utility_last_error(utility)

    actual fun clear_utility(utility: OlmUtilityPointer): ULong = olm_clear_utility(utility).toULong()

    actual fun sha256_length(utility: OlmUtilityPointer): ULong = olm_sha256_length(utility).toULong()

    actual fun sha256(
        utility: OlmUtilityPointer,
        input: ByteArray,
        output: ByteArray,
    ): ULong = input.withNativeRead { inputPtr, inputSize ->
        output.withNativeWrite { outputPtr, outputSize ->
            olm_sha256(utility, inputPtr, inputSize, outputPtr, outputSize).toULong()
        }
    }

    actual fun ed25519_verify(
        utility: OlmUtilityPointer,
        key: ByteArray,
        message: ByteArray,
        signature: ByteArray,
    ): ULong = key.withNativeRead { keyPtr, keySize ->
        message.withNativeRead { messagePtr, messageSize ->
            signature.withNativeRead { signaturePtr, signatureSize ->
                olm_ed25519_verify(
                    utility,
                    keyPtr, keySize,
                    messagePtr, messageSize,
                    signaturePtr, signatureSize
                ).toULong()
            }
        }
    }

    actual fun sas(): OlmSASPointer = genericInit(::olm_sas, olm_sas_size())

    actual fun sas_last_error(sas: OlmSASPointer): String? = olm_sas_last_error(sas)

    actual fun clear_sas(sas: OlmSASPointer): ULong = olm_clear_sas(sas).toULong()

    actual fun create_sas_random_length(sas: OlmSASPointer): ULong = olm_create_sas_random_length(sas).toULong()

    actual fun create_sas(sas: OlmSASPointer, random: ByteArray?): ULong =
        random.withNativeRead { randomPtr, randomSize ->
            olm_create_sas(sas, randomPtr, randomSize).toULong()
        }

    actual fun sas_pubkey_length(sas: OlmSASPointer): ULong = olm_sas_pubkey_length(sas).toULong()

    actual fun sas_get_pubkey(sas: OlmSASPointer, pubkey: ByteArray): ULong =
        pubkey.withNativeWrite { pubkeyPtr, pubkeySize ->
            olm_sas_get_pubkey(sas, pubkeyPtr, pubkeySize).toULong()
        }

    actual fun sas_set_their_key(sas: OlmSASPointer, theirKey: ByteArray): ULong =
        theirKey.withNativeRead { theirKeyPtr, theirKeySize ->
            olm_sas_set_their_key(sas, theirKeyPtr, theirKeySize).toULong()
        }

    actual fun sas_is_their_key_set(sas: OlmSASPointer): Int = olm_sas_is_their_key_set(sas)

    actual fun sas_generate_bytes(
        sas: OlmSASPointer,
        info: ByteArray,
        output: ByteArray
    ): ULong = info.withNativeRead { infoPtr, infoSize ->
        output.withNativeWrite { outputPtr, outputSize ->
            olm_sas_generate_bytes(sas, infoPtr, infoSize, outputPtr, outputSize).toULong()
        }
    }

    actual fun sas_mac_length(sas: OlmSASPointer): ULong = olm_sas_mac_length(sas).toULong()

    actual fun sas_calculate_mac(
        sas: OlmSASPointer,
        input: ByteArray,
        info: ByteArray,
        mac: ByteArray
    ): ULong = input.withNativeRead { inputPtr, inputSize ->
        info.withNativeRead { infoPtr, infoSize ->
            mac.withNativeWrite { macPtr, macSize ->
                olm_sas_calculate_mac(sas, inputPtr, inputSize, infoPtr, infoSize, macPtr, macSize).toULong()
            }
        }
    }

    actual fun sas_calculate_mac_ULong_kdf(
        sas: OlmSASPointer,
        input: ByteArray,
        info: ByteArray,
        mac: ByteArray
    ): ULong = input.withNativeRead { inputPtr, inputSize ->
        info.withNativeRead { infoPtr, infoSize ->
            mac.withNativeWrite { macPtr, macSize ->
                olm_sas_calculate_mac_long_kdf(sas, inputPtr, inputSize, infoPtr, infoSize, macPtr, macSize).toULong()
            }
        }
    }

    actual fun pk_encryption(): OlmPkEncryptionPointer = genericInit(::olm_pk_encryption, olm_pk_encryption_size())

    actual fun pk_encryption_last_error(encryption: OlmPkEncryptionPointer): String? =
        olm_pk_encryption_last_error(encryption)

    actual fun clear_pk_encryption(encryption: OlmPkEncryptionPointer): ULong =
        olm_clear_pk_encryption(encryption).toULong()

    actual fun pk_encryption_set_recipient_key(
        encryption: OlmPkEncryptionPointer,
        publicKey: ByteArray
    ): ULong = publicKey.withNativeRead { publicKeyPtr, publicKeySize ->
        olm_pk_encryption_set_recipient_key(encryption, publicKeyPtr, publicKeySize).toULong()
    }

    actual fun pk_ciphertext_length(
        encryption: OlmPkEncryptionPointer,
        plainTextLength: ULong
    ): ULong = olm_pk_ciphertext_length(encryption, NativeSize(plainTextLength)).toULong()

    actual fun pk_mac_length(encryption: OlmPkEncryptionPointer): ULong = olm_pk_mac_length(encryption).toULong()

    actual fun pk_key_length(): ULong = olm_pk_key_length().toULong()

    actual fun pk_encrypt_random_length(encryption: OlmPkEncryptionPointer): ULong =
        olm_pk_encrypt_random_length(encryption).toULong()

    actual fun pk_encrypt(
        encryption: OlmPkEncryptionPointer,
        plainText: ByteArray,
        cipherText: ByteArray,
        mac: ByteArray,
        ephemeralKey: ByteArray,
        random: ByteArray?
    ): ULong = plainText.withNativeRead { plainTextPtr, plainTextSize ->
        cipherText.withNativeWrite { cipherTextPtr, cipherTextSize ->
            mac.withNativeWrite { macPtr, macSize ->
                ephemeralKey.withNativeWrite { ephemeralPtr, ephemeralSize ->
                    random.withNativeRead { randomPtr, randomSize ->
                        olm_pk_encrypt(
                            encryption,
                            plainTextPtr, plainTextSize,
                            cipherTextPtr, cipherTextSize,
                            macPtr, macSize,
                            ephemeralPtr, ephemeralSize,
                            randomPtr, randomSize
                        ).toULong()
                    }
                }
            }
        }
    }

    actual fun pk_decryption(): OlmPkDecryptionPointer = genericInit(::olm_pk_decryption, olm_pk_decryption_size())

    actual fun pk_decryption_last_error(decryption: OlmPkDecryptionPointer): String? =
        olm_pk_decryption_last_error(decryption)

    actual fun clear_pk_decryption(decryption: OlmPkDecryptionPointer): ULong =
        olm_clear_pk_decryption(decryption).toULong()

    actual fun pk_private_key_length(): ULong = olm_pk_private_key_length().toULong()

    actual fun pk_key_from_private(
        decryption: OlmPkDecryptionPointer,
        pubkey: ByteArray,
        privkey: ByteArray
    ): ULong = pubkey.withNativeWrite { pubkeyPtr, pubkeySize ->
        privkey.withNativeRead { privkeyPtr, privkeySize ->
            olm_pk_key_from_private(decryption, pubkeyPtr, pubkeySize, privkeyPtr, privkeySize).toULong()
        }
    }

    actual fun pickle_pk_decryption_length(decryption: OlmPkDecryptionPointer): ULong =
        olm_pickle_pk_decryption_length(decryption).toULong()

    actual fun pickle_pk_decryption(
        decryption: OlmPkDecryptionPointer,
        key: ByteArray,
        pickled: ByteArray
    ): ULong = key.withNativeRead { keyPtr, keySize ->
        pickled.withNativeWrite { pickledPtr, pickledSize ->
            olm_pickle_pk_decryption(decryption, keyPtr, keySize, pickledPtr, pickledSize).toULong()
        }
    }

    actual fun unpickle_pk_decryption(
        decryption: OlmPkDecryptionPointer,
        key: ByteArray,
        pickled: ByteArray,
        pubkey: ByteArray
    ): ULong = key.withNativeRead { keyPtr, keySize ->
        pickled.withNativeRead { pickledPtr, pickledSize ->
            pubkey.withNativeRead { pubkeyPtr, pubkeySize ->
                olm_unpickle_pk_decryption(
                    decryption,
                    keyPtr, keySize,
                    pickledPtr, pickledSize,
                    pubkeyPtr, pubkeySize
                ).toULong()
            }
        }
    }

    actual fun pk_max_plaintext_length(
        decryption: OlmPkDecryptionPointer,
        cipherTextLength: ULong
    ): ULong = olm_pk_max_plaintext_length(decryption, NativeSize(cipherTextLength)).toULong()

    actual fun pk_decrypt(
        decryption: OlmPkDecryptionPointer,
        ephemeralKey: ByteArray,
        mac: ByteArray,
        cipherText: ByteArray,
        plainText: ByteArray
    ): ULong = ephemeralKey.withNativeRead { ephemeralPtr, ephemeralSize ->
        mac.withNativeRead { macPtr, macSize ->
            cipherText.withNativeRead { cipherTextPtr, cipherTextSize ->
                plainText.withNativeWrite { plainTextPtr, plainTextSize ->
                    olm_pk_decrypt(
                        decryption,
                        ephemeralPtr, ephemeralSize,
                        macPtr, macSize,
                        cipherTextPtr, cipherTextSize,
                        plainTextPtr, plainTextSize,
                    ).toULong()
                }
            }
        }
    }

    actual fun pk_get_private_key(
        decryption: OlmPkDecryptionPointer,
        privateKey: ByteArray
    ): ULong = privateKey.withNativeWrite { privateKeyPtr, privateKeySize ->
        olm_pk_get_private_key(decryption, privateKeyPtr, privateKeySize).toULong()
    }

    actual fun pk_signing(): OlmPkSigningPointer = genericInit(::olm_pk_signing, olm_pk_signing_size())

    actual fun pk_signing_last_error(sign: OlmPkSigningPointer): String? = olm_pk_signing_last_error(sign)

    actual fun clear_pk_signing(sign: OlmPkSigningPointer): ULong = olm_clear_pk_signing(sign).toULong()

    actual fun pk_signing_key_from_seed(
        sign: OlmPkSigningPointer,
        pubkey: ByteArray,
        seed: ByteArray
    ): ULong = pubkey.withNativeWrite { pubkeyPtr, pubkeySize ->
        seed.withNativeRead { seedPtr, seedSize ->
            olm_pk_signing_key_from_seed(sign, pubkeyPtr, pubkeySize, seedPtr, seedSize).toULong()
        }
    }

    actual fun pk_signing_seed_length(): ULong = olm_pk_signing_seed_length().toULong()

    actual fun pk_signing_public_key_length(): ULong = olm_pk_signing_public_key_length().toULong()

    actual fun pk_signature_length(): ULong = olm_pk_signature_length().toULong()

    actual fun pk_sign(
        sign: OlmPkSigningPointer,
        message: ByteArray,
        signature: ByteArray
    ): ULong = message.withNativeRead { messagePtr, messageSize ->
        signature.withNativeWrite { signaturePtr, signatureSize ->
            olm_pk_sign(sign, messagePtr, messageSize, signaturePtr, signatureSize).toULong()
        }
    }
}