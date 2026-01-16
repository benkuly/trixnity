package de.connect2x.trixnity.client.mocks

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import de.connect2x.trixnity.client.key.KeyBackupService
import de.connect2x.trixnity.clientserverapi.model.key.GetRoomKeysBackupVersionResponse
import de.connect2x.trixnity.core.model.RoomId

class KeyBackupServiceMock : KeyBackupService {
    override val version: MutableStateFlow<GetRoomKeysBackupVersionResponse.V1?> = MutableStateFlow(null)

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