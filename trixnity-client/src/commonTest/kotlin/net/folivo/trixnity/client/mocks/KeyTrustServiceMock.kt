package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel
import net.folivo.trixnity.client.key.IKeyTrustService
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.SignedCrossSigningKeys
import net.folivo.trixnity.core.model.keys.SignedDeviceKeys

class KeyTrustServiceMock : IKeyTrustService {
    val trustAndSignKeysCalled = MutableStateFlow<Pair<Set<Key.Ed25519Key>, UserId>?>(null)
    override suspend fun trustAndSignKeys(keys: Set<Key.Ed25519Key>, userId: UserId) {
        trustAndSignKeysCalled.value = keys to userId
    }

    override suspend fun calculateDeviceKeysTrustLevel(deviceKeys: SignedDeviceKeys): KeySignatureTrustLevel {
        throw NotImplementedError()
    }

    override suspend fun calculateCrossSigningKeysTrustLevel(crossSigningKeys: SignedCrossSigningKeys): KeySignatureTrustLevel {
        throw NotImplementedError()
    }

    override suspend fun updateTrustLevelOfKeyChainSignedBy(
        signingUserId: UserId,
        signingKey: Key.Ed25519Key,
        visitedKeys: MutableSet<Pair<UserId, String?>>
    ) {
        throw NotImplementedError()
    }
}