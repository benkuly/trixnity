package net.folivo.trixnity.client.mocks

import net.folivo.trixnity.client.key.IKeySecretService
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent

class KeySecretServiceMock : IKeySecretService {
    override suspend fun decryptMissingSecrets(key: ByteArray, keyId: String, keyInfo: SecretKeyEventContent) {
        throw NotImplementedError()
    }
}