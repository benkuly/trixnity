package net.folivo.trixnity.crypto.olm

import net.folivo.trixnity.clientserverapi.model.keys.ClaimKeys
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ToDeviceEventContent
import net.folivo.trixnity.core.model.keys.KeyAlgorithm
import net.folivo.trixnity.core.model.keys.Keys

interface OlmServiceRequestHandler {
    suspend fun setOneTimeKeys(
        oneTimeKeys: Keys? = null,
    ): Result<Unit>

    suspend fun claimKeys(
        oneTimeKeys: Map<UserId, Map<String, KeyAlgorithm>>,
    ): Result<ClaimKeys.Response>

    suspend fun <C : ToDeviceEventContent> sendToDevice(
        events: Map<UserId, Map<String, C>>,
    ): Result<Unit>
}