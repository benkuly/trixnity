package de.connect2x.trixnity.crypto.olm

import de.connect2x.trixnity.clientserverapi.model.sync.OneTimeKeysCount
import de.connect2x.trixnity.clientserverapi.model.sync.UnusedFallbackKeyTypes

interface OlmKeysChangeEmitter {
    fun subscribeOneTimeKeysCount(subscriber: suspend (OlmKeysChange) -> Unit): () -> Unit
}

data class OlmKeysChange(
    val oneTimeKeysCount: OneTimeKeysCount?,
    val fallbackKeyTypes: UnusedFallbackKeyTypes?,
)