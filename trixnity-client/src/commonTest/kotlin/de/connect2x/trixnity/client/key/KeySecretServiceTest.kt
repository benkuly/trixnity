package de.connect2x.trixnity.client.key

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.getInMemoryGlobalAccountDataStore
import de.connect2x.trixnity.client.getInMemoryKeyStore
import de.connect2x.trixnity.client.mockMatrixClientServerApiClient
import de.connect2x.trixnity.client.store.StoredSecret
import de.connect2x.trixnity.clientserverapi.model.user.SetGlobalAccountData
import de.connect2x.trixnity.core.MSC3814
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import de.connect2x.trixnity.core.model.events.GlobalAccountDataEventContent
import de.connect2x.trixnity.core.model.events.m.DehydratedDeviceEventContent
import de.connect2x.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import de.connect2x.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import de.connect2x.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import de.connect2x.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key.AesHmacSha2EncryptedData
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.crypto.SecretType.*
import de.connect2x.trixnity.crypto.core.decryptAesHmacSha2
import de.connect2x.trixnity.crypto.core.encryptAesHmacSha2
import de.connect2x.trixnity.crypto.key.convert
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.testutils.PortableMockEngineConfig
import de.connect2x.trixnity.testutils.matrixJsonEndpoint
import de.connect2x.trixnity.utils.encodeUnpaddedBase64
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