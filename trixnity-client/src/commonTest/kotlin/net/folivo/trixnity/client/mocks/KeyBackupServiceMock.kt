package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.client.key.IKeyBackupService
import net.folivo.trixnity.clientserverapi.model.keys.GetRoomKeysBackupVersionResponse
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.keys.Key

class KeyBackupServiceMock : IKeyBackupService {
    override val version: StateFlow<GetRoomKeysBackupVersionResponse.V1?>
        get() = throw NotImplementedError()

    override fun loadMegolmSession(roomId: RoomId, sessionId: String, senderKey: Key.Curve25519Key) {
        throw NotImplementedError()
    }

    var returnKeyBackupCanBeTrusted: Boolean = true
    override suspend fun keyBackupCanBeTrusted(
        keyBackupVersion: GetRoomKeysBackupVersionResponse,
        privateKey: String
    ): Boolean {
        return returnKeyBackupCanBeTrusted
    }

    override suspend fun bootstrapRoomKeyBackup(
        key: ByteArray,
        keyId: String,
        masterSigningPrivateKey: String,
        masterSigningPublicKey: String
    ): Result<Unit> {
        throw NotImplementedError()
    }
}