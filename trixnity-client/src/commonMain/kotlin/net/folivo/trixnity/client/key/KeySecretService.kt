package net.folivo.trixnity.client.key

import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.folivo.trixnity.client.store.GlobalAccountDataStore
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.crypto.key.decryptSecret

private val log = KotlinLogging.logger {}

interface IKeySecretService {
    suspend fun decryptMissingSecrets(
        key: ByteArray,
        keyId: String,
        keyInfo: SecretKeyEventContent,
    )
}

class KeySecretService(
    private val json: Json,
    private val keyStore: KeyStore,
    private val globalAccountDataStore: GlobalAccountDataStore,
) : IKeySecretService {

    override suspend fun decryptMissingSecrets(
        key: ByteArray,
        keyId: String,
        keyInfo: SecretKeyEventContent,
    ) {
        val decryptedSecrets = SecretType.values()
            .subtract(keyStore.secrets.value.keys)
            .mapNotNull { allowedSecret ->
                val event = allowedSecret.getEncryptedSecret(globalAccountDataStore)
                if (event != null) {
                    kotlin.runCatching {
                        decryptSecret(key, keyId, keyInfo, allowedSecret.id, event.content, json)
                    }.getOrNull()
                        ?.let { allowedSecret to StoredSecret(event, it) }
                } else {
                    log.warn { "could not find secret ${allowedSecret.id} to decrypt and cache" }
                    null
                }
            }.toMap()
        keyStore.secrets.update {
            it + decryptedSecrets
        }
    }
}