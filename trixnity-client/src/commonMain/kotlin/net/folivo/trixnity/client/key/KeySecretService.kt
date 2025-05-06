package net.folivo.trixnity.client.key

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import net.folivo.trixnity.client.store.GlobalAccountDataStore
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.crypto.SecretType
import net.folivo.trixnity.crypto.key.decryptSecret

private val log = KotlinLogging.logger("net.folivo.trixnity.client.key.KeySecretService")

interface KeySecretService {
    suspend fun decryptMissingSecrets(
        key: ByteArray,
        keyId: String,
        keyInfo: SecretKeyEventContent,
    )
}

class KeySecretServiceImpl(
    private val json: Json,
    private val keyStore: KeyStore,
    private val globalAccountDataStore: GlobalAccountDataStore,
) : KeySecretService {

    override suspend fun decryptMissingSecrets(
        key: ByteArray,
        keyId: String,
        keyInfo: SecretKeyEventContent,
    ) {
        val decryptedSecrets = SecretType.entries
            .filter { it.cacheable }
            .subtract(keyStore.getSecrets().keys)
            .mapNotNull { allowedSecret ->
                val event = allowedSecret.getEncryptedSecret(globalAccountDataStore).first()
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
        keyStore.updateSecrets {
            it + decryptedSecrets
        }
    }
}