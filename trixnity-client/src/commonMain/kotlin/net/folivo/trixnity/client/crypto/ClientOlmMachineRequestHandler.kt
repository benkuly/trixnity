package net.folivo.trixnity.client.crypto

import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.model.keys.ClaimKeys
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.core.model.keys.Keys
import net.folivo.trixnity.crypto.olm.OlmMachineRequestHandler

class ClientOlmMachineRequestHandler(private val api: MatrixClientServerApiClient) : OlmMachineRequestHandler {
    override suspend fun setOneTimeKeys(oneTimeKeys: Keys?): Result<Unit> =
        api.keys.setKeys(oneTimeKeys = oneTimeKeys).map { }

    override suspend fun claimKeys(oneTimeKeys: Map<UserId, Map<String, KeyAlgorithm>>): Result<ClaimKeys.Response> =
        api.keys.claimKeys(oneTimeKeys)

    override suspend fun <C : ToDeviceEventContent> sendToDevice(events: Map<UserId, Map<String, C>>): Result<Unit> =
        api.users.sendToDevice(events)
}