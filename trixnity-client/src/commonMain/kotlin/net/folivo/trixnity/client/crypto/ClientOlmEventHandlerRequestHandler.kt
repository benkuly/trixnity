package net.folivo.trixnity.client.crypto

import net.folivo.trixnity.clientserverapi.client.IMatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.core.model.keys.Keys
import net.folivo.trixnity.crypto.olm.OlmEventHandlerRequestHandler

class ClientOlmEventHandlerRequestHandler(private val api: IMatrixClientServerApiClient) :
    OlmEventHandlerRequestHandler {
    override suspend fun setOneTimeKeys(oneTimeKeys: Keys?): Result<Unit> =
        api.keys.setKeys(oneTimeKeys = oneTimeKeys).map { }
}