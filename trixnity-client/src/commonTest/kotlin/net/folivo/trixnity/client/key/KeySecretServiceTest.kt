package net.folivo.trixnity.client.key

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import net.folivo.trixnity.client.MatrixClientConfiguration
import net.folivo.trixnity.client.getInMemoryGlobalAccountDataStore
import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.mockMatrixClientServerApiClient
import net.folivo.trixnity.client.store.StoredSecret
import net.folivo.trixnity.clientserverapi.model.user.SetGlobalAccountData
import net.folivo.trixnity.core.MSC3814
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.GlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.m.DehydratedDeviceEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key.AesHmacSha2EncryptedData
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.crypto.SecretType.*
import net.folivo.trixnity.crypto.core.decryptAesHmacSha2
import net.folivo.trixnity.crypto.core.encryptAesHmacSha2
import net.folivo.trixnity.crypto.key.convert
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import net.folivo.trixnity.utils.encodeUnpaddedBase64
import kotlin.random.Random
import kotlin.test.Test

@OptIn(MSC3814::class)
class KeySecretServiceTest : TrixnityBaseTest() {

    private val userId = UserId("alice", "server")
    private val json = createMatrixEventJson()
    private val keyStore = getInMemoryKeyStore()
    private val globalAccountDataStore = getInMemoryGlobalAccountDataStore()

    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(apiConfig)

    private val cut = KeySecretServiceImpl(
        json = json,
        keyStore = keyStore,
        globalAccountDataStore = globalAccountDataStore,
        api = api,
        userInfo = UserInfo(userId, "device", Key.Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
        matrixClientConfiguration = MatrixClientConfiguration().apply { experimentalFeatures.enableMSC3814 = true }
    )

    @Test
    fun `decryptOrCreateMissingSecrets » decrypt missing secrets and update secure store`() = runTest {
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
        val secret = Random.nextBytes(32).encodeUnpaddedBase64()
        val encryptedData = encryptAesHmacSha2(
            content = secret.encodeToByteArray(),
            key = key,
            name = "m.cross_signing.user_signing"
        ).convert()

        val event = GlobalAccountDataEvent(
            UserSigningKeyEventContent(mapOf("KEY" to json.encodeToJsonElement(encryptedData)))
        )
        globalAccountDataStore.save(event)

        cut.decryptOrCreateMissingSecrets(key, "KEY", SecretKeyEventContent.AesHmacSha2Key())
        keyStore.getSecrets() shouldBe existingPrivateKeys + mapOf(
            M_CROSS_SIGNING_USER_SIGNING to StoredSecret(event, secret),
        )
    }

    @Test
    fun `decryptOrCreateMissingSecrets » create missing secrets and update secure store`() = runTest {
        val key = Random.nextBytes(32)
        var setAccountData: GlobalAccountDataEventContent? = null
        apiConfig.endpoints {
            matrixJsonEndpoint(
                SetGlobalAccountData(userId, "org.matrix.msc3814"),
            ) {
                setAccountData = it
            }
        }

        cut.decryptOrCreateMissingSecrets(key, "KEY", SecretKeyEventContent.AesHmacSha2Key())
        val dehydratedDeviceEventContent =
            setAccountData.shouldNotBeNull().shouldBeInstanceOf<DehydratedDeviceEventContent>()
        val storedSecret = keyStore.getSecrets()[M_DEHYDRATED_DEVICE].shouldNotBeNull()
        storedSecret.event.content shouldBe dehydratedDeviceEventContent
        val storedSecretPrivateKeys = storedSecret.decryptedPrivateKey
        val encryptedData =
            json.decodeFromJsonElement<AesHmacSha2EncryptedData>(dehydratedDeviceEventContent.encrypted["KEY"].shouldNotBeNull())
        decryptAesHmacSha2(
            content = encryptedData.convert(),
            key = key,
            name = M_DEHYDRATED_DEVICE.id
        ).decodeToString() shouldBe storedSecretPrivateKeys
    }
}