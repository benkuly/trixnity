package net.folivo.trixnity.client.crypto

import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.OlmKeysChangeSubscriber
import net.folivo.trixnity.crypto.olm.OlmKeysChangeEmitter

class ClientOlmKeysChangeEmitter(private val api: MatrixClientServerApiClient) : OlmKeysChangeEmitter {
    override fun subscribeOneTimeKeysCount(subscriber: OlmKeysChangeSubscriber) {
        api.sync.subscribeOlmKeysChange(subscriber)
    }

    override fun unsubscribeOneTimeKeysCount(subscriber: OlmKeysChangeSubscriber) {
        api.sync.unsubscribeOlmKeysChange(subscriber)
    }
}