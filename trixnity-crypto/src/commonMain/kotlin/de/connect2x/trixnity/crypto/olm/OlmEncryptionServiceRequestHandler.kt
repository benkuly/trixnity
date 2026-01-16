package de.connect2x.trixnity.crypto.olm

import de.connect2x.trixnity.clientserverapi.model.key.ClaimKeys
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ToDeviceEventContent
import de.connect2x.trixnity.core.model.keys.KeyAlgorithm

interface OlmEncryptionServiceRequestHandler {
    suspend fun claimKeys(
        oneTimeKeys: Map<UserId, Map<String, KeyAlgorithm>>,
    ): Result<ClaimKeys.Response>

    suspend fun sendToDevice(
        events: Map<UserId, Map<String, ToDeviceEventContent>>,
    ): Result<Unit>
}