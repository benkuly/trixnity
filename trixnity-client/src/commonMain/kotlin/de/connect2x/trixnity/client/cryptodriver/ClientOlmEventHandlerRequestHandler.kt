package de.connect2x.trixnity.client.cryptodriver

import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.core.model.keys.Keys
import de.connect2x.trixnity.crypto.olm.OlmEventHandlerRequestHandler

class ClientOlmEventHandlerRequestHandler(private val api: MatrixClientServerApiClient) :
    OlmEventHandlerRequestHandler {
    override suspend fun setOneTimeKeys(oneTimeKeys: Keys?, fallbackKeys: Keys?): Result<Unit> =
        api.key.setKeys(oneTimeKeys = oneTimeKeys, fallbackKeys = fallbackKeys).map { }
}