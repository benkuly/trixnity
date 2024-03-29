package net.folivo.trixnity.crypto.key

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.util.*
import kotlinx.serialization.json.encodeToJsonElement
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.core.encryptAesHmacSha2
import kotlin.random.Random

class KeySecretUtilsTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {
    timeout = 30_000

    val json = createMatrixEventJson()

    should("decrypt decryptSecret ${SecretKeyEventContent.AesHmacSha2Key::class.simpleName}") {
        val key = Random.nextBytes(32)
        val secret = Random.nextBytes(32).encodeBase64()
        val encryptedData = encryptAesHmacSha2(
            content = secret.encodeToByteArray(),
            key = key,
            name = "m.cross_signing.user_signing"
        )
        decryptSecret(
            key = key,
            keyId = "KEY",
            keyInfo = SecretKeyEventContent.AesHmacSha2Key(),
            secretName = "m.cross_signing.user_signing",
            secret = UserSigningKeyEventContent(mapOf("KEY" to json.encodeToJsonElement(encryptedData.convert()))),
            json = json
        ) shouldBe secret
    }
    should("throw error on decryptSecret error") {
        val secret = Random.nextBytes(32)
        val encryptedData = encryptAesHmacSha2(
            content = secret,
            key = Random.nextBytes(32),
            name = "m.cross_signing.user_signing"
        )
        shouldThrowAny {
            decryptSecret(
                key = Random.nextBytes(32),
                keyId = "KEY",
                keyInfo = SecretKeyEventContent.AesHmacSha2Key(),
                secretName = "m.cross_signing.user_signing",
                secret = UserSigningKeyEventContent(mapOf("KEY" to json.encodeToJsonElement(encryptedData))),
                json = json
            )
        }
    }
}