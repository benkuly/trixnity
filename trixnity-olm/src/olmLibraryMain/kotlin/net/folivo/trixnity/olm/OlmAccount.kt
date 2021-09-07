package net.folivo.trixnity.olm

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.folivo.trixnity.olm.OlmLibrary.account
import net.folivo.trixnity.olm.OlmLibrary.account_fallback_key
import net.folivo.trixnity.olm.OlmLibrary.account_fallback_key_length
import net.folivo.trixnity.olm.OlmLibrary.account_generate_fallback_key
import net.folivo.trixnity.olm.OlmLibrary.account_generate_fallback_key_random_length
import net.folivo.trixnity.olm.OlmLibrary.account_generate_one_time_keys
import net.folivo.trixnity.olm.OlmLibrary.account_generate_one_time_keys_random_length
import net.folivo.trixnity.olm.OlmLibrary.account_identity_keys
import net.folivo.trixnity.olm.OlmLibrary.account_identity_keys_length
import net.folivo.trixnity.olm.OlmLibrary.account_last_error
import net.folivo.trixnity.olm.OlmLibrary.account_mark_keys_as_published
import net.folivo.trixnity.olm.OlmLibrary.account_max_number_of_one_time_keys
import net.folivo.trixnity.olm.OlmLibrary.account_one_time_keys
import net.folivo.trixnity.olm.OlmLibrary.account_one_time_keys_length
import net.folivo.trixnity.olm.OlmLibrary.account_sign
import net.folivo.trixnity.olm.OlmLibrary.account_signature_length
import net.folivo.trixnity.olm.OlmLibrary.clear_account
import net.folivo.trixnity.olm.OlmLibrary.create_account
import net.folivo.trixnity.olm.OlmLibrary.create_account_random_length
import net.folivo.trixnity.olm.OlmLibrary.pickle_account
import net.folivo.trixnity.olm.OlmLibrary.pickle_account_length
import net.folivo.trixnity.olm.OlmLibrary.remove_one_time_keys
import net.folivo.trixnity.olm.OlmLibrary.unpickle_account

actual class OlmAccount private constructor() : WantsToBeFree {
    internal actual val ptr: OlmAccountPointer = account()

    actual companion object {
        actual fun create(): OlmAccount =
            OlmAccount().apply {
                try {
                    val randomSize = create_account_random_length(ptr)
                    val result = withRandom(randomSize) { create_account(ptr, it) }
                    checkError(ptr, result, ::account_last_error)
                } catch (e: Exception) {
                    free()
                    throw e
                }
            }

        actual fun unpickle(key: String, pickle: String): OlmAccount =
            OlmAccount().apply {
                try {
                    val result = unpickle_account(ptr, key.encodeToByteArray(), pickle.encodeToByteArray())
                    checkError(ptr, result, ::account_last_error)
                } catch (e: Exception) {
                    free()
                    throw e
                }
            }
    }

    actual val identityKeys: OlmIdentityKeys
        get() {
            val identityKeys = ByteArray(account_identity_keys_length(ptr).toInt())
            val size = checkResult { account_identity_keys(ptr, identityKeys) }
            val identityKeysString = identityKeys.decodeToString(endIndex = size.toInt())
            return Json.decodeFromString(identityKeysString)
        }

    actual val fallbackKey: OlmOneTimeKeys
        get() {
            val keys = ByteArray(account_fallback_key_length(ptr).toInt())
            val size = checkResult { account_fallback_key(ptr, keys) }
            val keysString = keys.decodeToString(endIndex = size.toInt())
            return Json.decodeFromString(keysString)
        }

    actual val oneTimeKeys: OlmOneTimeKeys
        get() {
            val keys = ByteArray(account_one_time_keys_length(ptr).toInt())
            val size = checkResult { account_one_time_keys(ptr, keys) }
            val keysString = keys.decodeToString(endIndex = size.toInt())
            return Json.decodeFromString(keysString)
        }

    actual val maxNumberOfOneTimeKeys: Long
        get() = account_max_number_of_one_time_keys(ptr).toLong()

    actual override fun free() {
        clear_account(ptr)
        ptr.free()
    }

    actual fun pickle(key: String): String =
        pickle(ptr, key, ::pickle_account_length, ::pickle_account, ::account_last_error)

    actual fun sign(message: String): String {
        val signature = ByteArray(account_signature_length(ptr).toInt())
        checkResult { account_sign(ptr, message.encodeToByteArray(), signature) }
        return signature.decodeToString()
    }

    actual fun markOneTimeKeysAsPublished() {
        checkResult { account_mark_keys_as_published(ptr) }
    }

    actual fun generateOneTimeKeys(numberOfKeys: Long) {
        if (numberOfKeys <= 0) throw OlmLibraryException("SHOULD_BE_POSIIVE")
        checkResult {
            withRandom(account_generate_one_time_keys_random_length(ptr, numberOfKeys.toULong())) { random ->
                account_generate_one_time_keys(ptr, numberOfKeys.toULong(), random)
            }
        }
    }

    actual fun removeOneTimeKeys(session: OlmSession) {
        checkResult {
            remove_one_time_keys(ptr, session.ptr)
        }
    }

    actual fun generateFallbackKey() {
        checkResult {
            withRandom(account_generate_fallback_key_random_length(ptr)) { random ->
                account_generate_fallback_key(ptr, random)
            }
        }
    }

    private fun checkResult(block: () -> ULong): ULong = checkError(ptr, block(), ::account_last_error)

}