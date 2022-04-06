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

    lateinit var returnCalculateDeviceKeysTrustLevel: KeySignatureTrustLevel
    override suspend fun calculateDeviceKeysTrustLevel(deviceKeys: SignedDeviceKeys): KeySignatureTrustLevel {
        return returnCalculateDeviceKeysTrustLevel
    }

    lateinit var returnCalculateCrossSigningKeysTrustLevel: KeySignatureTrustLevel
    override suspend fun calculateCrossSigningKeysTrustLevel(crossSigningKeys: SignedCrossSigningKeys): KeySignatureTrustLevel {
        return returnCalculateCrossSigningKeysTrustLevel
    }

    val updateTrustLevelOfKeyChainSignedByCalled =
        MutableStateFlow<Pair<UserId, Key.Ed25519Key>?>(null)

    override suspend fun updateTrustLevelOfKeyChainSignedBy(
        signingUserId: UserId,
        signingKey: Key.Ed25519Key,
    ) {
        updateTrustLevelOfKeyChainSignedByCalled.value = Pair(signingUserId, signingKey)
    }
}