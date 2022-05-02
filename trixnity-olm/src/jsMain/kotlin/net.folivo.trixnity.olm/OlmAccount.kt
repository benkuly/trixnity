package net.folivo.trixnity.olm

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

actual class OlmAccount private constructor() : WantsToBeFree {
    internal actual val ptr: OlmAccountPointer = rethrow { js("new Olm.Account()") }.unsafeCast<OlmAccountPointer>()

    actual companion object {
        actual suspend fun create(): OlmAccount {
            initOlm()
            return OlmAccount()
                .apply { rethrow { ptr.create() } }
        }

        actual suspend fun unpickle(key: String, pickle: String): OlmAccount {
            initOlm()
            return OlmAccount().apply {
                rethrow { ptr.unpickle(key, pickle) }
            }
        }
    }

    actual val identityKeys: OlmIdentityKeys get() = Json.decodeFromString(rethrow { ptr.identity_keys() })
    actual val unpublishedFallbackKey: OlmOneTimeKeys get() = Json.decodeFromString(rethrow { ptr.unpublished_fallback_key() })
    actual val oneTimeKeys: OlmOneTimeKeys get() = Json.decodeFromString(rethrow { ptr.one_time_keys() })
    actual val maxNumberOfOneTimeKeys: Long get() = rethrow { ptr.max_number_of_one_time_keys() }.toLong()

    actual override fun free() = ptr.free()

    actual fun pickle(key: String): String = rethrow { ptr.pickle(key) }

    actual fun sign(message: String): String = rethrow { ptr.sign(message) }

    actual fun markKeysAsPublished() = rethrow { ptr.mark_keys_as_published() }

    actual fun generateOneTimeKeys(numberOfKeys: Long) {
        require(numberOfKeys > 0) { "number of keys requested for generation must be positive" }
        rethrow { ptr.generate_one_time_keys(numberOfKeys) }
    }

    actual fun removeOneTimeKeys(session: OlmSession) = rethrow { ptr.remove_one_time_keys(session.ptr) }

    actual fun forgetOldFallbackKey() = rethrow { ptr.forget_old_fallback_key() }

    actual fun generateFallbackKey() = rethrow { ptr.generate_fallback_key() }

}