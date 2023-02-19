package net.folivo.trixnity.crypto.mocks

import net.folivo.trixnity.clientserverapi.model.keys.ClaimKeys
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.crypto.olm.OlmEncryptionServiceRequestHandler

class OlmEncryptionServiceRequestHandlerMock : OlmEncryptionServiceRequestHandler {
    val claimKeysParams = mutableListOf<Map<UserId, Map<String, KeyAlgorithm>>>()
    var claimKeys: Result<ClaimKeys.Response>? = null
    override suspend fun claimKeys(oneTimeKeys: Map<UserId, Map<String, KeyAlgorithm>>): Result<ClaimKeys.Response> {
        claimKeysParams.add(oneTimeKeys)
        return checkNotNull(claimKeys)
    }

    val sendToDeviceParams = mutableListOf<Map<UserId, Map<String, ToDeviceEventContent>>>()
    var sendToDevice: Result<Unit>? = null
    override suspend fun <C : ToDeviceEventContent> sendToDevice(events: Map<UserId, Map<String, C>>): Result<Unit> {
        sendToDeviceParams.add(events)
        return sendToDevice ?: Result.success(Unit)
    }
}