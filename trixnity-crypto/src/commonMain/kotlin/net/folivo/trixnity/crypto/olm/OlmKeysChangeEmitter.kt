package net.folivo.trixnity.crypto.olm

import net.folivo.trixnity.clientserverapi.client.OlmKeysChangeSubscriber

interface OlmKeysChangeEmitter {
    fun subscribeOneTimeKeysCount(subscriber: OlmKeysChangeSubscriber)
    fun unsubscribeOneTimeKeysCount(subscriber: OlmKeysChangeSubscriber)
}