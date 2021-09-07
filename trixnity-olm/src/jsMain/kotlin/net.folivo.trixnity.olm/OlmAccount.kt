package net.folivo.trixnity.olm

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import rethrow

actual class OlmAccount private constructor() : WantsToBeFree {
    internal actual val ptr: OlmAccountPointer = rethrow { js("new Olm.Account()") }.unsafeCast<OlmAccountPointer>()

    actual companion object {
        actual fun create(): OlmAccount = OlmAccount()
            .apply { rethrow { ptr.create() } }

        actual fun unpickle(key: String, pickle: String): OlmAccount =
            OlmAccount().apply {
                rethrow { ptr.unpickle(key, pickle) }
            }
    }

    actual val identityKeys: OlmIdentityKeys get() = Json.decodeFromString(rethrow { ptr.identity_keys() })
    actual val fallbackKey: OlmOneTimeKeys get() = Json.decodeFromString(rethrow { ptr.fallback_key() })
    actual val oneTimeKeys: OlmOneTimeKeys get() = Json.decodeFromString(rethrow { ptr.one_time_keys() })
    actual val maxNumberOfOneTimeKeys: Long get() = rethrow { ptr.max_number_of_one_time_keys() }.toLong()

    actual override fun free() = ptr.free()

    actual fun pickle(key: String): String = rethrow { ptr.pickle(key) }

    actual fun sign(message: String): String = rethrow { ptr.sign(message) }

    actual fun markOneTimeKeysAsPublished() = rethrow { ptr.mark_keys_as_published() }

    actual fun generateOneTimeKeys(numberOfKeys: Long) {
        if (numberOfKeys <= 0) throw OlmLibraryException("SHOULD_BE_POSIIVE")
        rethrow { ptr.generate_one_time_keys(numberOfKeys) }
    }

    actual fun removeOneTimeKeys(session: OlmSession) = rethrow { ptr.remove_one_time_keys(session.ptr) }

    actual fun generateFallbackKey() = rethrow { ptr.generate_fallback_key() }

}