package net.folivo.trixnity.client.crypto

import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.ClientEventEmitter.Priority
import net.folivo.trixnity.crypto.olm.OlmKeysChange
import net.folivo.trixnity.crypto.olm.OlmKeysChangeEmitter

class ClientOlmKeysChangeEmitter(private val api: MatrixClientServerApiClient) : OlmKeysChangeEmitter {
    override fun subscribeOneTimeKeysCount(subscriber: suspend (OlmKeysChange) -> Unit) =
        api.sync.subscribe(Priority.ONE_TIME_KEYS) {
            subscriber(OlmKeysChange(it.syncResponse.oneTimeKeysCount, it.syncResponse.unusedFallbackKeyTypes))
        }
}