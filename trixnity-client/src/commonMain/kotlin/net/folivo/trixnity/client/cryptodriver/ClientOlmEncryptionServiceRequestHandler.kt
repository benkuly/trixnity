package net.folivo.trixnity.client.cryptodriver

import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.key.ClaimKeys
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.crypto.olm.OlmEncryptionServiceRequestHandler

class ClientOlmEncryptionServiceRequestHandler(private val api: MatrixClientServerApiClient) :
    OlmEncryptionServiceRequestHandler {
    override suspend fun claimKeys(oneTimeKeys: Map<UserId, Map<String, KeyAlgorithm>>): Result<ClaimKeys.Response> =
        api.key.claimKeys(oneTimeKeys)

    override suspend fun sendToDevice(events: Map<UserId, Map<String, ToDeviceEventContent>>): Result<Unit> =
        api.user.sendToDevice(events)
}