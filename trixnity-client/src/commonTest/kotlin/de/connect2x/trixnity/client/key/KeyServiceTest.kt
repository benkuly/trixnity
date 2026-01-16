package de.connect2x.trixnity.client.key

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.beEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import de.connect2x.trixnity.client.*
import de.connect2x.trixnity.client.mocks.KeyBackupServiceMock
import de.connect2x.trixnity.client.mocks.KeyTrustServiceMock
import de.connect2x.trixnity.client.mocks.RoomServiceMock
import de.connect2x.trixnity.client.mocks.SignServiceMock
import de.connect2x.trixnity.client.store.KeySignatureTrustLevel
import de.connect2x.trixnity.client.store.KeySignatureTrustLevel.Valid
import de.connect2x.trixnity.client.store.StoredCrossSigningKeys
import de.connect2x.trixnity.client.store.StoredDeviceKeys
import de.connect2x.trixnity.clientserverapi.client.UIA
import de.connect2x.trixnity.clientserverapi.model.key.SetCrossSigningKeys
import de.connect2x.trixnity.clientserverapi.model.uia.ResponseWithUIA
import de.connect2x.trixnity.clientserverapi.model.user.SetGlobalAccountData
import de.connect2x.trixnity.core.MSC3814
import de.connect2x.trixnity.core.UserInfo
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.DehydratedDeviceEventContent
import de.connect2x.trixnity.core.model.events.m.crosssigning.MasterKeyEventContent
import de.connect2x.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import de.connect2x.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import de.connect2x.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import de.connect2x.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key
import de.connect2x.trixnity.core.model.keys.*
import de.connect2x.trixnity.core.model.keys.Key.Ed25519Key
import de.connect2x.trixnity.crypto.SecretType.*
import de.connect2x.trixnity.crypto.driver.CryptoDriver
import de.connect2x.trixnity.crypto.driver.vodozemac.VodozemacCryptoDriver
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.scheduleSetup
import de.connect2x.trixnity.testutils.PortableMockEngineConfig
import de.connect2x.trixnity.testutils.matrixJsonEndpoint
import kotlin.test.Test

@OptIn(MSC3814::class)
class KeyServiceTest : TrixnityBaseTest() {

    private val driver: CryptoDriver = VodozemacCryptoDriver

    private val alice = UserId("alice", "server")
    private val aliceDevice = "ALICEDEVICE"

    private val signServiceMock = SignServiceMock()
    private val roomServiceMock = RoomServiceMock()
    private val keyBackupServiceMock = KeyBackupServiceMock()
    private val keyTrustServiceMock = KeyTrustServiceMock()

    private val keyStore = getInMemoryKeyStore()
    private val olmCryptoStore = getInMemoryOlmStore()
    private val globalAccountDataStore = getInMemoryGlobalAccountDataStore()

    private val apiConfig = PortableMockEngineConfig()
    private val api = mockMatrixClientServerApiClient(apiConfig)

    private val cut = KeyServiceImpl(
        userInfo = UserInfo(alice, aliceDevice, Ed25519Key(null, ""), Key.Curve25519Key(null, "")),
        keyStore = keyStore,
        olmCryptoStore = olmCryptoStore,
        globalAccountDataStore = globalAccountDataStore,
        roomService = roomServiceMock,
        signService = signServiceMock,
        keyBackupService = keyBackupServiceMock,
        keyTrustService = keyTrustServiceMock,
        api = api,
        matrixClientConfiguration = MatrixClientConfiguration().apply { experimentalFeatures.enableMSC3814 = true },
        driver = driver,
    )

    private var secretKeyEventContentCalled = false
    private var capturedPassphrase: AesHmacSha2Key.SecretStorageKeyPassphrase? = null
    private var defaultSecretKeyEventContentCalled = false
    private var masterKeyEventContentCalled = false
    private var userSigningKeyEventContentCalled = false
    private var selfSigningKeyEventContentCalled = false
    private var dehydratedDeviceEventContentCalled = false
    private var setCrossSigningKeysCalled = false

    init {
        apiConfig.endpoints {
            matrixJsonEndpoint(
                SetGlobalAccountData(alice, "m.secret_storage.key.*"),
            ) {
                it.shouldBeInstanceOf<AesHmacSha2Key>()
                it.iv shouldNot beEmpty()
                it.mac shouldNot beEmpty()
                capturedPassphrase = it.passphrase
                secretKeyEventContentCalled = true
            }
            matrixJsonEndpoint(
                SetGlobalAccountData(alice, "m.secret_storage.default_key")
            ) {
                it.shouldBeInstanceOf<DefaultSecretKeyEventContent>()
                it.key.length shouldBeGreaterThan 10
                defaultSecretKeyEventContentCalled = true
            }
            matrixJsonEndpoint(
                SetGlobalAccountData(alice, "m.cross_signing.master")
            ) {
                it.shouldBeInstanceOf<MasterKeyEventContent>()
                val encrypted = it.encrypted.values.first()
                encrypted.shouldBeInstanceOf<JsonObject>()
                encrypted["iv"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                encrypted["mac"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                masterKeyEventContentCalled = true
            }
            matrixJsonEndpoint(
                SetGlobalAccountData(alice, "m.cross_signing.user_signing")
            ) {
                it.shouldBeInstanceOf<UserSigningKeyEventContent>()
                val encrypted = it.encrypted.values.first()
                encrypted.shouldBeInstanceOf<JsonObject>()
                encrypted["iv"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                encrypted["mac"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                userSigningKeyEventContentCalled = true
            }
            matrixJsonEndpoint(
                SetGlobalAccountData(alice, "m.cross_signing.self_signing")
            ) {
                it.shouldBeInstanceOf<SelfSigningKeyEventContent>()
                val encrypted = it.encrypted.values.first()
                encrypted.shouldBeInstanceOf<JsonObject>()
                encrypted["iv"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                encrypted["mac"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                selfSigningKeyEventContentCalled = true
            }
            matrixJsonEndpoint(
                SetGlobalAccountData(alice, "org.matrix.msc3814")
            ) {
                it.shouldBeInstanceOf<DehydratedDeviceEventContent>()
                val encrypted = it.encrypted.values.first()
                encrypted.shouldBeInstanceOf<JsonObject>()
                encrypted["iv"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                encrypted["mac"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                dehydratedDeviceEventContentCalled = true
            }
            matrixJsonEndpoint(SetCrossSigningKeys) {
                it.request.masterKey shouldNotBe null
                it.request.selfSigningKey shouldNotBe null
                it.request.userSigningKey shouldNotBe null
                setCrossSigningKeysCalled = true
                ResponseWithUIA.Success(Unit)
            }
        }

        scheduleSetup {
            keyStore.updateCrossSigningKeys(alice) {
                setOf(
                    StoredCrossSigningKeys(
                        SignedCrossSigningKeys(
                            CrossSigningKeys(
                                alice, setOf(CrossSigningKeysUsage.MasterKey), keysOf(
                                    Ed25519Key("A_MSK", "A_MSK")
                                )
                            ), mapOf()
                        ), Valid(false)
                    )
                )
            }
            keyStore.updateDeviceKeys(alice) {
                mapOf(
                    aliceDevice to StoredDeviceKeys(
                        SignedDeviceKeys(
                            DeviceKeys(
                                alice, aliceDevice, setOf(),
                                keysOf(Ed25519Key(aliceDevice, "dev"))
                            ), mapOf()
                        ),
                        Valid(false)
                    )
                )
            }
        }
    }

    @Test
    fun `bootstrapCrossSigning » successfull » bootstrap`() = runTest {
        backgroundScope.launch {
            while (currentCoroutineContext().isActive) {
                keyStore.getOutdatedKeysFlow().first { it.contains(alice) }
                keyStore.updateOutdatedKeys { setOf() }
            }
        }
        backgroundScope.launch {
            keyTrustServiceMock.trustAndSignKeysCalled.filterNotNull().first()
            keyStore.updateDeviceKeys(alice) {
                mapOf(
                    aliceDevice to StoredDeviceKeys(
                        SignedDeviceKeys(
                            DeviceKeys(
                                alice, aliceDevice, setOf(),
                                keysOf(Ed25519Key(aliceDevice, "dev"))
                            ), mapOf()
                        ),
                        KeySignatureTrustLevel.CrossSigned(true)
                    )
                )
            }
        }
        val result = cut.bootstrapCrossSigning()

        assertSoftly(result) {
            this.recoveryKey shouldNot beEmpty()
            this.result shouldBe Result.success(UIA.Success(Unit))
        }
        keyTrustServiceMock.trustAndSignKeysCalled.value shouldBe (setOf(
            Ed25519Key("A_MSK", "A_MSK"),
            Ed25519Key(aliceDevice, "dev")
        ) to alice)
        keyBackupServiceMock.bootstrapRoomKeyBackupCalled.value shouldBe true
        keyStore.getSecrets().keys shouldBe setOf(
            M_CROSS_SIGNING_SELF_SIGNING,
            M_CROSS_SIGNING_USER_SIGNING,
            M_DEHYDRATED_DEVICE,
        )
        secretKeyEventContentCalled shouldBe true
        capturedPassphrase shouldBe null
        defaultSecretKeyEventContentCalled shouldBe true
        masterKeyEventContentCalled shouldBe true
        userSigningKeyEventContentCalled shouldBe true
        selfSigningKeyEventContentCalled shouldBe true
        dehydratedDeviceEventContentCalled shouldBe true
        setCrossSigningKeysCalled shouldBe true
    }

    @Test
    fun `bootstrapCrossSigning » successfull » bootstrap from passphrase`() = runTest {
        backgroundScope.launch {
            while (currentCoroutineContext().isActive) {
                keyStore.getOutdatedKeysFlow().first { it.contains(alice) }
                keyStore.updateOutdatedKeys { setOf() }
            }
        }
        backgroundScope.launch {
            keyTrustServiceMock.trustAndSignKeysCalled.filterNotNull().first()
            keyStore.updateDeviceKeys(alice) {
                mapOf(
                    aliceDevice to StoredDeviceKeys(
                        SignedDeviceKeys(
                            DeviceKeys(
                                alice, aliceDevice, setOf(),
                                keysOf(Ed25519Key(aliceDevice, "dev"))
                            ), mapOf()
                        ),
                        KeySignatureTrustLevel.CrossSigned(true)
                    )
                )
            }
        }
        val result = cut.bootstrapCrossSigningFromPassphrase("super secret. not.")
        assertSoftly(result) {
            this.recoveryKey shouldNot beEmpty()
            this.result shouldBe Result.success(UIA.Success(Unit))
        }
        keyTrustServiceMock.trustAndSignKeysCalled.value shouldBe (setOf(
            Ed25519Key("A_MSK", "A_MSK"),
            Ed25519Key(aliceDevice, "dev")
        ) to alice)
        keyBackupServiceMock.bootstrapRoomKeyBackupCalled.value shouldBe true
        keyStore.getSecrets().keys shouldBe setOf(
            M_CROSS_SIGNING_SELF_SIGNING,
            M_CROSS_SIGNING_USER_SIGNING,
            M_DEHYDRATED_DEVICE,
        )
        secretKeyEventContentCalled shouldBe true
        capturedPassphrase.shouldBeInstanceOf<AesHmacSha2Key.SecretStorageKeyPassphrase.Pbkdf2>()
        defaultSecretKeyEventContentCalled shouldBe true
        masterKeyEventContentCalled shouldBe true
        userSigningKeyEventContentCalled shouldBe true
        selfSigningKeyEventContentCalled shouldBe true
        dehydratedDeviceEventContentCalled shouldBe true
        setCrossSigningKeysCalled shouldBe true
    }


}