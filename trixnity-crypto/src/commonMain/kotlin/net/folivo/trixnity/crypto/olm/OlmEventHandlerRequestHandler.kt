package net.folivo.trixnity.crypto.olm

import net.folivo.trixnity.core.model.keys.Keys

interface OlmEventHandlerRequestHandler {
    suspend fun setOneTimeKeys(
        oneTimeKeys: Keys?,
        fallbackKeys: Keys?,
    ): Result<Unit>
}