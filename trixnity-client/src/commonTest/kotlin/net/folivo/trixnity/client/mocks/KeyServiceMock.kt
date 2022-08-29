package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.client.key.DeviceTrustLevel
import net.folivo.trixnity.client.key.IKeyService
import net.folivo.trixnity.client.key.UserTrustLevel
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.keys.CrossSigningKeys
import net.folivo.trixnity.core.model.keys.DeviceKeys

class KeyServiceMock(
    override val bootstrapRunning: StateFlow<Boolean> = MutableStateFlow(false)
) : IKeyService {
    override suspend fun bootstrapCrossSigning(
        recoveryKey: ByteArray,
        secretKeyEventContentGenerator: suspend () -> SecretKeyEventContent
    ): IKeyService.BootstrapCrossSigning {
        throw NotImplementedError()
    }

    override suspend fun bootstrapCrossSigningFromPassphrase(
        passphrase: String,
        secretKeyEventContentGenerator: suspend () -> Pair<ByteArray, SecretKeyEventContent>
    ): IKeyService.BootstrapCrossSigning {
        throw NotImplementedError()
    }

    override suspend fun getTrustLevel(
        userId: UserId,
        deviceId: String,
        scope: CoroutineScope
    ): StateFlow<DeviceTrustLevel> {
        throw NotImplementedError()
    }

    override suspend fun getTrustLevel(
        timelineEvent: TimelineEvent,
        scope: CoroutineScope
    ): StateFlow<DeviceTrustLevel>? {
        throw NotImplementedError()
    }

    override suspend fun getTrustLevel(userId: UserId, scope: CoroutineScope): StateFlow<UserTrustLevel> {
        throw NotImplementedError()
    }

    override suspend fun getDeviceKeys(userId: UserId, scope: CoroutineScope): StateFlow<List<DeviceKeys>?> {
        throw NotImplementedError()
    }

    override suspend fun getDeviceKeys(userId: UserId): List<DeviceKeys>? {
        throw NotImplementedError()
    }

    override suspend fun getCrossSigningKeys(
        userId: UserId,
        scope: CoroutineScope
    ): StateFlow<List<CrossSigningKeys>?> {
        throw NotImplementedError()
    }

    override suspend fun getCrossSigningKeys(userId: UserId): List<CrossSigningKeys>? {
        throw NotImplementedError()
    }
}