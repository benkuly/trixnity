package net.folivo.trixnity.client.cryptodriver

import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.model.keys.Keys
import net.folivo.trixnity.crypto.olm.OlmEventHandlerRequestHandler

class ClientOlmEventHandlerRequestHandler(private val api: MatrixClientServerApiClient) :
    OlmEventHandlerRequestHandler {
    override suspend fun setOneTimeKeys(oneTimeKeys: Keys?, fallbackKeys: Keys?): Result<Unit> =
        api.key.setKeys(oneTimeKeys = oneTimeKeys, fallbackKeys = fallbackKeys).map { }
}