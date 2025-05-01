package net.folivo.trixnity.client.key

import io.kotest.matchers.shouldBe
import io.ktor.util.*
import kotlinx.serialization.json.encodeToJsonElement
import net.folivo.trixnity.client.getInMemoryGlobalAccountDataStore
import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.SecretType.*
import net.folivo.trixnity.crypto.core.encryptAesHmacSha2
import net.folivo.trixnity.crypto.key.convert
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import kotlin.random.Random
import kotlin.test.Test

class KeySecretServiceTest : TrixnityBaseTest() {

    private val json = createMatrixEventJson()
    private val keyStore = getInMemoryKeyStore()
    private val globalAccountDataStore = getInMemoryGlobalAccountDataStore()

    private val cut = KeySecretServiceImpl(json, keyStore, globalAccountDataStore)

    @Test
    fun `decryptMissingSecrets » decrypt missing secrets and update secure store`() = runTest {
        val existingPrivateKeys = mapOf(
            M_CROSS_SIGNING_SELF_SIGNING to StoredSecret(
                GlobalAccountDataEvent(SelfSigningKeyEventContent(mapOf())), "key2"
            ),
            M_MEGOLM_BACKUP_V1 to StoredSecret(
                GlobalAccountDataEvent(SelfSigningKeyEventContent(mapOf())), "key3"
            )
        )
        keyStore.updateSecrets { existingPrivateKeys }

        val key = Random.nextBytes(32)
        val secret = Random.nextBytes(32).encodeBase64()
        val encryptedData = encryptAesHmacSha2(
            content = secret.encodeToByteArray(),
            key = key,
            name = "m.cross_signing.user_signing"
        ).convert()

        val event = GlobalAccountDataEvent(
            UserSigningKeyEventContent(mapOf("KEY" to json.encodeToJsonElement(encryptedData)))
        )
        globalAccountDataStore.save(event)

        cut.decryptMissingSecrets(key, "KEY", SecretKeyEventContent.AesHmacSha2Key())
        keyStore.getSecrets() shouldBe existingPrivateKeys + mapOf(
            M_CROSS_SIGNING_USER_SIGNING to StoredSecret(event, secret),
        )
    }
}