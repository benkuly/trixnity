package de.connect2x.trixnity.client.cryptodriver

import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.core.ClientEventEmitter.Priority
import de.connect2x.trixnity.crypto.olm.OlmKeysChange
import de.connect2x.trixnity.crypto.olm.OlmKeysChangeEmitter

class ClientOlmKeysChangeEmitter(private val api: MatrixClientServerApiClient) : OlmKeysChangeEmitter {
    override fun subscribeOneTimeKeysCount(subscriber: suspend (OlmKeysChange) -> Unit) =
        api.sync.subscribe(Priority.ONE_TIME_KEYS) {
            subscriber(OlmKeysChange(it.syncResponse.oneTimeKeysCount, it.syncResponse.unusedFallbackKeyTypes))
        }
}