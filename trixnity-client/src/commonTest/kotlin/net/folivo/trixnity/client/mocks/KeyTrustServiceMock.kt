package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.client.key.KeyTrustService
import net.folivo.trixnity.client.store.KeySignatureTrustLevel
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.SignedCrossSigningKeys
import net.folivo.trixnity.core.model.keys.SignedDeviceKeys

class KeyTrustServiceMock : KeyTrustService {
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

    var returnCheckOwnAdvertisedMasterKeyAndVerifySelf: Result<Unit> = Result.success(Unit)
    val checkOwnAdvertisedMasterKeyAndVerifySelfCalled =
        MutableStateFlow<Triple<ByteArray, String, SecretKeyEventContent>?>(null)

    override suspend fun checkOwnAdvertisedMasterKeyAndVerifySelf(
        key: ByteArray,
        keyId: String,
        keyInfo: SecretKeyEventContent
    ): Result<Unit> {
        checkOwnAdvertisedMasterKeyAndVerifySelfCalled.value = Triple(key, keyId, keyInfo)
        return returnCheckOwnAdvertisedMasterKeyAndVerifySelf
    }
}