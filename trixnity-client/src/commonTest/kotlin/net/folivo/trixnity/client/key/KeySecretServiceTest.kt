package net.folivo.trixnity.client.key

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.encodeToJsonElement
import net.folivo.trixnity.client.getInMemoryGlobalAccountDataStore
import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.store.GlobalAccountDataStore
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.core.model.events.Event.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.SecretType.*
import net.folivo.trixnity.crypto.encryptAesHmacSha2
import kotlin.random.Random

class KeySecretServiceTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {
    timeout = 30_000

    val json = createMatrixEventJson()
    lateinit var scope: CoroutineScope
    lateinit var keyStore: KeyStore
    lateinit var globalAccountDataStore: GlobalAccountDataStore


    lateinit var cut: KeySecretServiceImpl

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        keyStore = getInMemoryKeyStore(scope)
        globalAccountDataStore = getInMemoryGlobalAccountDataStore(scope)
        cut = KeySecretServiceImpl(json, keyStore, globalAccountDataStore)
    }

    afterTest {
        scope.cancel()
    }

    context(KeySecretServiceImpl::decryptMissingSecrets.name) {
        should("decrypt missing secrets and update secure store") {
            val existingPrivateKeys = mapOf(
                M_CROSS_SIGNING_SELF_SIGNING to StoredSecret(
                    GlobalAccountDataEvent(SelfSigningKeyEventContent(mapOf())), "key2"
                ),
                M_MEGOLM_BACKUP_V1 to StoredSecret(
                    GlobalAccountDataEvent(SelfSigningKeyEventContent(mapOf())), "key3"
                )
            )
            keyStore.secrets.value = existingPrivateKeys

            val key = Random.nextBytes(32)
            val secret = Random.nextBytes(32).encodeBase64()
            val encryptedData = encryptAesHmacSha2(
                content = secret.encodeToByteArray(),
                key = key,
                name = "m.cross_signing.user_signing"
            )

            val event = GlobalAccountDataEvent(
                UserSigningKeyEventContent(mapOf("KEY" to json.encodeToJsonElement(encryptedData)))
            )
            globalAccountDataStore.update(event)

            cut.decryptMissingSecrets(key, "KEY", SecretKeyEventContent.AesHmacSha2Key())
            keyStore.secrets.value shouldBe existingPrivateKeys + mapOf(
                M_CROSS_SIGNING_USER_SIGNING to StoredSecret(event, secret),
            )
        }
    }
}