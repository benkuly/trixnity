package de.connect2x.trixnity.crypto.olm

import de.connect2x.trixnity.core.model.keys.Keys

interface OlmEventHandlerRequestHandler {
    suspend fun setOneTimeKeys(
        oneTimeKeys: Keys?,
        fallbackKeys: Keys?,
    ): Result<Unit>
}