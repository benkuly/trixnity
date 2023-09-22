package net.folivo.trixnity.crypto.olm

import net.folivo.trixnity.clientserverapi.model.sync.OneTimeKeysCount
import net.folivo.trixnity.clientserverapi.model.sync.UnusedFallbackKeyTypes

interface OlmKeysChangeEmitter {
    fun subscribeOneTimeKeysCount(subscriber: suspend (OlmKeysChange) -> Unit): () -> Unit
}

data class OlmKeysChange(
    val oneTimeKeysCount: OneTimeKeysCount?,
    val fallbackKeyTypes: UnusedFallbackKeyTypes?,
)