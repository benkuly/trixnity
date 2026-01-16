package de.connect2x.trixnity.crypto.mocks

import de.connect2x.trixnity.clientserverapi.model.key.ClaimKeys
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ToDeviceEventContent
import de.connect2x.trixnity.core.model.keys.KeyAlgorithm
import de.connect2x.trixnity.crypto.olm.OlmEncryptionServiceRequestHandler

class OlmEncryptionServiceRequestHandlerMock : OlmEncryptionServiceRequestHandler {
    val claimKeysParams = mutableListOf<Map<UserId, Map<String, KeyAlgorithm>>>()
    var claimKeys: Result<ClaimKeys.Response>? = null
    override suspend fun claimKeys(oneTimeKeys: Map<UserId, Map<String, KeyAlgorithm>>): Result<ClaimKeys.Response> {
        claimKeysParams.add(oneTimeKeys)
        return checkNotNull(claimKeys)
    }

    val sendToDeviceParams = mutableListOf<Map<UserId, Map<String, ToDeviceEventContent>>>()
    var sendToDevice: Result<Unit>? = null
    override suspend fun sendToDevice(events: Map<UserId, Map<String, ToDeviceEventContent>>): Result<Unit> {
        sendToDeviceParams.add(events)
        return sendToDevice ?: Result.success(Unit)
    }
}