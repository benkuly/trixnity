package net.folivo.trixnity.client.mocks

import kotlinx.coroutines.flow.MutableStateFlow
import net.folivo.trixnity.client.key.KeySecretService
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent

class KeySecretServiceMock : KeySecretService {
    val decryptMissingSecretsCalled = MutableStateFlow<Triple<ByteArray, String, SecretKeyEventContent>?>(null)
    override suspend fun decryptMissingSecrets(key: ByteArray, keyId: String, keyInfo: SecretKeyEventContent) {
        decryptMissingSecretsCalled.value = Triple(key, keyId, keyInfo)
    }
}