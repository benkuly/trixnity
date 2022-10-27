package net.folivo.trixnity.client.crypto

import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.keys.ClaimKeys
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.crypto.olm.OlmEncryptionServiceRequestHandler

class ClientOlmEncryptionServiceRequestHandler(private val api: MatrixClientServerApiClient) :
    OlmEncryptionServiceRequestHandler {
    override suspend fun claimKeys(oneTimeKeys: Map<UserId, Map<String, KeyAlgorithm>>): Result<ClaimKeys.Response> =
        api.keys.claimKeys(oneTimeKeys)

    override suspend fun <C : ToDeviceEventContent> sendToDevice(events: Map<UserId, Map<String, C>>): Result<Unit> =
        api.users.sendToDevice(events)
}