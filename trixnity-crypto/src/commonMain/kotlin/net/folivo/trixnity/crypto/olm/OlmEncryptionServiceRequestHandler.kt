package net.folivo.trixnity.crypto.olm

import net.folivo.trixnity.clientserverapi.model.key.ClaimKeys
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.keys.KeyAlgorithm

interface OlmEncryptionServiceRequestHandler {
    suspend fun claimKeys(
        oneTimeKeys: Map<UserId, Map<String, KeyAlgorithm>>,
    ): Result<ClaimKeys.Response>

    suspend fun sendToDevice(
        events: Map<UserId, Map<String, ToDeviceEventContent>>,
    ): Result<Unit>
}