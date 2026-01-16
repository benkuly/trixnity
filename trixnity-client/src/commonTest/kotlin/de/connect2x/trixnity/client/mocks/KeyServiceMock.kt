package de.connect2x.trixnity.client.mocks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import de.connect2x.trixnity.client.key.KeyService
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import de.connect2x.trixnity.core.model.keys.CrossSigningKeys
import de.connect2x.trixnity.core.model.keys.DeviceKeys
import de.connect2x.trixnity.crypto.key.DeviceTrustLevel
import de.connect2x.trixnity.crypto.key.UserTrustLevel

class KeyServiceMock(
    override val bootstrapRunning: StateFlow<Boolean> = MutableStateFlow(false)
) : KeyService {
    override suspend fun bootstrapCrossSigning(
        recoveryKey: ByteArray,
        secretKeyEventContent: SecretKeyEventContent,
    ): KeyService.BootstrapCrossSigning {
        throw NotImplementedError()
    }

    override suspend fun bootstrapCrossSigning(): KeyService.BootstrapCrossSigning {
        throw NotImplementedError()
    }

    override suspend fun bootstrapCrossSigningFromPassphrase(passphrase: String): KeyService.BootstrapCrossSigning {
        throw NotImplementedError()
    }

    override fun getTrustLevel(
        userId: UserId,
        deviceId: String,
    ): StateFlow<DeviceTrustLevel> {
        throw NotImplementedError()
    }

    override fun getTrustLevel(roomId: RoomId, eventId: EventId): Flow<DeviceTrustLevel?> {
        throw NotImplementedError()
    }

    override fun getTrustLevel(userId: UserId): StateFlow<UserTrustLevel> {
        throw NotImplementedError()
    }

    override fun getDeviceKeys(userId: UserId): StateFlow<List<DeviceKeys>?> {
        throw NotImplementedError()
    }


    override fun getCrossSigningKeys(
        userId: UserId,
    ): StateFlow<List<CrossSigningKeys>?> {
        throw NotImplementedError()
    }
}