package net.folivo.trixnity.client.key

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.beEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.*
import net.folivo.trixnity.client.mocks.KeyBackupServiceMock
import net.folivo.trixnity.client.mocks.KeyTrustServiceMock
import net.folivo.trixnity.client.mocks.RoomServiceMock
import net.folivo.trixnity.client.mocks.SignServiceMock
import net.folivo.trixnity.client.store.KeySignatureTrustLevel
import net.folivo.trixnity.client.store.KeySignatureTrustLevel.Valid
import net.folivo.trixnity.client.store.StoredCrossSigningKeys
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.clientserverapi.client.UIA
import net.folivo.trixnity.clientserverapi.model.keys.SetCrossSigningKeys
import net.folivo.trixnity.clientserverapi.model.uia.ResponseWithUIA
import net.folivo.trixnity.clientserverapi.model.users.SetGlobalAccountData
import net.folivo.trixnity.core.MSC3814
import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.DehydratedDeviceEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.MasterKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.AesHmacSha2Key
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import net.folivo.trixnity.crypto.SecretType.*
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import net.folivo.trixnity.test.utils.scheduleSetup
import net.folivo.trixnity.testutils.PortableMockEngineConfig
import net.folivo.trixnity.testutils.matrixJsonEndpoint
import kotlin.test.Test

@OptIn(MSC3814::class)
class KeyServiceTest : TrixnityBaseTest() {
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
            matrixJsonEndpoint(SetCrossSigningKeys()) {
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
            keyStore.getOutdatedKeysFlow().first { it.contains(alice) }
            keyStore.updateOutdatedKeys { setOf() }
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
            keyStore.getOutdatedKeysFlow().first { it.contains(alice) }
            keyStore.updateOutdatedKeys { setOf() }
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