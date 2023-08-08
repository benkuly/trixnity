package net.folivo.trixnity.crypto.olm

import net.folivo.trixnity.clientserverapi.client.OlmKeysChange

interface OlmKeysChangeEmitter {
    fun subscribeOneTimeKeysCount(subscriber: suspend (OlmKeysChange) -> Unit)
    fun unsubscribeOneTimeKeysCount(subscriber: suspend (OlmKeysChange) -> Unit)
}