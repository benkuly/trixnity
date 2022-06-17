package net.folivo.trixnity.client.key

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import mu.KotlinLogging
import net.folivo.trixnity.client.crypto.decryptAesHmacSha2
import net.folivo.trixnity.client.crypto.encryptAesHmacSha2
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key.AesHmacSha2EncryptedData

private val log = KotlinLogging.logger {}

internal suspend fun decryptSecret(
    key: ByteArray,
    keyId: String,
    keyInfo: SecretKeyEventContent,
    secretName: String,
    secret: SecretEventContent,
    json: Json
): String? {
    log.trace { "try decrypt secret $secretName with key $keyId" }
    val encryptedSecret = secret.encrypted[keyId] ?: return null
    return when (keyInfo) {
        is SecretKeyEventContent.AesHmacSha2Key -> {
            try {
                val encryptedData = json.decodeFromJsonElement<AesHmacSha2EncryptedData>(encryptedSecret)
                decryptAesHmacSha2(
                    content = encryptedData,
                    key = key,
                    name = secretName
                ).decodeToString().also { log.debug { "decrypted secret $secretName" } }
            } catch (error: Throwable) {
                log.warn(error) { "could not decrypt secret $secretName ($secret)" }
                null
            }
        }
        is SecretKeyEventContent.Unknown -> null
    }
}

internal suspend fun encryptSecret(
    key: ByteArray,
    keyId: String,
    secretName: String,
    secret: String,
    json: Json
): Map<String, JsonElement> {
    return mapOf(
        keyId to json.encodeToJsonElement(
            encryptAesHmacSha2(
                content = secret.encodeToByteArray(),
                key = key,
                name = secretName
            )
        )
    )
}