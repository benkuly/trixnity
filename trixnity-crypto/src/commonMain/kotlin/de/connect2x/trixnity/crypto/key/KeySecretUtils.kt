package de.connect2x.trixnity.crypto.key

import de.connect2x.lognity.api.logger.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import de.connect2x.trixnity.core.model.events.m.secretstorage.SecretEventContent
import de.connect2x.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import de.connect2x.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key.AesHmacSha2EncryptedData
import de.connect2x.trixnity.crypto.core.decryptAesHmacSha2
import de.connect2x.trixnity.crypto.core.encryptAesHmacSha2

private val log = Logger("de.connect2x.trixnity.crypto.key.KeySecretUtils")

suspend fun decryptSecret(
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
            val encryptedData = json.decodeFromJsonElement<AesHmacSha2EncryptedData>(encryptedSecret)
            decryptAesHmacSha2(
                content = encryptedData.convert(),
                key = key,
                name = secretName
            ).decodeToString().also { log.debug { "decrypted secret $secretName" } }
        }

        is SecretKeyEventContent.Unknown -> throw IllegalArgumentException("unknown secret not supported")
    }
}

suspend fun encryptSecret(
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
            ).convert()
        )
    )
}

// TODO internal as soon as all crypto stuff is moved to trixnity-crypto
fun AesHmacSha2EncryptedData.convert() =
    de.connect2x.trixnity.crypto.core.AesHmacSha2EncryptedData(iv = iv, ciphertext = ciphertext, mac = mac)

// TODO internal as soon as all crypto stuff is moved to trixnity-crypto
fun de.connect2x.trixnity.crypto.core.AesHmacSha2EncryptedData.convert() =
    AesHmacSha2EncryptedData(iv = iv, ciphertext = ciphertext, mac = mac)