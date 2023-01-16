package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import net.folivo.trixnity.client.key.KeyBackupService
import net.folivo.trixnity.clientserverapi.model.keys.GetRoomKeysBackupVersionResponse
import net.folivo.trixnity.core.model.RoomId

class KeyBackupServiceMock : KeyBackupService {
    override val version: StateFlow<GetRoomKeysBackupVersionResponse.V1?>
        get() = throw NotImplementedError()

    val loadMegolmSessionCalled = MutableStateFlow<List<Pair<RoomId, String>>>(listOf())
    override suspend fun loadMegolmSession(roomId: RoomId, sessionId: String) {
        loadMegolmSessionCalled.update { it + Pair(roomId, sessionId) }
    }

    var returnKeyBackupCanBeTrusted: Boolean = true
    override suspend fun keyBackupCanBeTrusted(
        keyBackupVersion: GetRoomKeysBackupVersionResponse,
        privateKey: String
    ): Boolean {
        return returnKeyBackupCanBeTrusted
    }

    var returnBootstrapRoomKeyBackup: Result<Unit> = Result.success(Unit)
    val bootstrapRoomKeyBackupCalled = MutableStateFlow(false)
    override suspend fun bootstrapRoomKeyBackup(
        key: ByteArray,
        keyId: String,
        masterSigningPrivateKey: String,
        masterSigningPublicKey: String
    ): Result<Unit> {
        bootstrapRoomKeyBackupCalled.value = true
        return returnBootstrapRoomKeyBackup
    }
}