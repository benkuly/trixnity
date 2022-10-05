package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.client.key.DeviceTrustLevel
import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.client.key.UserTrustLevel
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.keys.CrossSigningKeys
import net.folivo.trixnity.core.model.keys.DeviceKeys

class KeyServiceMock(
    override val bootstrapRunning: StateFlow<Boolean> = MutableStateFlow(false)
) : KeyService {
    override suspend fun bootstrapCrossSigning(
        recoveryKey: ByteArray,
        secretKeyEventContentGenerator: suspend () -> SecretKeyEventContent
    ): KeyService.BootstrapCrossSigning {
        throw NotImplementedError()
    }

    override suspend fun bootstrapCrossSigningFromPassphrase(
        passphrase: String,
        secretKeyEventContentGenerator: suspend () -> Pair<ByteArray, SecretKeyEventContent>
    ): KeyService.BootstrapCrossSigning {
        throw NotImplementedError()
    }

    override suspend fun getTrustLevel(
        userId: UserId,
        deviceId: String,
    ): StateFlow<DeviceTrustLevel> {
        throw NotImplementedError()
    }

    override suspend fun getTrustLevel(
        timelineEvent: TimelineEvent,
    ): StateFlow<DeviceTrustLevel>? {
        throw NotImplementedError()
    }

    override suspend fun getTrustLevel(userId: UserId): StateFlow<UserTrustLevel> {
        throw NotImplementedError()
    }

    override suspend fun getDeviceKeys(userId: UserId): StateFlow<List<DeviceKeys>?> {
        throw NotImplementedError()
    }


    override suspend fun getCrossSigningKeys(
        userId: UserId,
    ): StateFlow<List<CrossSigningKeys>?> {
        throw NotImplementedError()
    }
}