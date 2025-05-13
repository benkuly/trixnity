package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.keys.CrossSigningKeys
import net.folivo.trixnity.core.model.keys.DeviceKeys
import net.folivo.trixnity.crypto.key.DeviceTrustLevel
import net.folivo.trixnity.crypto.key.UserTrustLevel

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