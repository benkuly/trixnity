package net.folivo.trixnity.client.crypto

import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.OlmKeysChange
import net.folivo.trixnity.crypto.olm.OlmKeysChangeEmitter

class ClientOlmKeysChangeEmitter(private val api: MatrixClientServerApiClient) : OlmKeysChangeEmitter {
    override fun subscribeOneTimeKeysCount(subscriber: suspend (OlmKeysChange) -> Unit) {
        api.sync.olmKeysChange.subscribe(subscriber)
    }

    override fun unsubscribeOneTimeKeysCount(subscriber: suspend (OlmKeysChange) -> Unit) {
        api.sync.olmKeysChange.unsubscribe(subscriber)
    }
}