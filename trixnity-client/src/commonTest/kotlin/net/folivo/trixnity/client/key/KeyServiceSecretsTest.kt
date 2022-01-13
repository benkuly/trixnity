package net.folivo.trixnity.client.key

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.timing.continually
import io.kotest.assertions.timing.eventually
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.spec.style.scopes.ShouldSpecContainerScope
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.util.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.SyncApiClient
import net.folivo.trixnity.client.api.UIA
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.*
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.crypto.OlmSignService
import net.folivo.trixnity.client.crypto.VerifyResult
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.store.AllowedSecretType.*
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.*
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.Event.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.m.KeyRequestAction
import net.folivo.trixnity.core.model.events.m.crosssigning.MasterKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.SelfSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.crosssigning.UserSigningKeyEventContent
import net.folivo.trixnity.core.model.events.m.secret.SecretKeyRequestEventContent
import net.folivo.trixnity.core.model.events.m.secret.SecretKeySendEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.DefaultSecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent.SecretStorageKeyPassphrase.Pbkdf2
import net.folivo.trixnity.core.serialization.createMatrixJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.olm.OlmPkSigning
import net.folivo.trixnity.olm.encodeUnpaddedBase64
import net.folivo.trixnity.olm.freeAfter
import kotlin.random.Random
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class KeyServiceKeyRequestsTest : ShouldSpec(body)

@OptIn(ExperimentalTime::class, InternalAPI::class)
private val body: ShouldSpec.() -> Unit = {
    timeout = 30_000

    val json = createMatrixJson()
    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val aliceDevice = "ALICEDEVICE"
    val bobDevice = "BOBDEVICE"
    lateinit var scope: CoroutineScope
    lateinit var store: Store
    val olm = mockk<OlmService>()
    val api = mockk<MatrixApiClient>()

    lateinit var cut: KeyService

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(scope).apply {
            init()
            account.userId.value = alice
            account.deviceId.value = aliceDevice
        }
        cut = KeyService(store, olm, api)
        coEvery { olm.sign.verify(any<SignedDeviceKeys>(), any()) } returns VerifyResult.Valid
        coEvery { api.json } returns json
    }

    afterTest {
        clearAllMocks()
        scope.cancel()
    }

    context(KeyService::handleEncryptedIncomingKeyRequests.name) {
        beforeTest {
            store.account.userId.value = alice
            store.keys.updateDeviceKeys(alice) {
                mapOf(aliceDevice to mockk { every { trustLevel } returns Valid(true) })
            }
            coEvery { api.users.sendToDevice<SecretKeySendEventContent>(any(), any()) } returns Result.success(Unit)
            store.keys.secrets.value =
                mapOf(M_CROSS_SIGNING_USER_SIGNING to StoredSecret(mockk(), "secretUserSigningKey"))
        }
        should("ignore request from other user") {
            cut.handleEncryptedIncomingKeyRequests(
                OlmService.DecryptedOlmEvent(
                    mockk(), Event.OlmEvent(
                        SecretKeyRequestEventContent(
                            M_CROSS_SIGNING_USER_SIGNING.id,
                            KeyRequestAction.REQUEST,
                            bobDevice,
                            "requestId"
                        ),
                        bob, keysOf(), alice, keysOf()
                    )
                )
            )
            cut.processIncomingKeyRequests()
            coVerify { api wasNot Called }
        }
        should("add request on request") {
            cut.handleEncryptedIncomingKeyRequests(
                OlmService.DecryptedOlmEvent(
                    mockk(), Event.OlmEvent(
                        SecretKeyRequestEventContent(
                            M_CROSS_SIGNING_USER_SIGNING.id,
                            KeyRequestAction.REQUEST,
                            aliceDevice,
                            "requestId"
                        ),
                        alice, keysOf(), alice, keysOf()
                    )
                )
            )
            cut.processIncomingKeyRequests()
            coVerify { api.users.sendToDevice<SecretKeySendEventContent>(any(), any(), any()) }
        }
        should("remove request on request cancellation") {
            cut.handleEncryptedIncomingKeyRequests(
                OlmService.DecryptedOlmEvent(
                    mockk(), Event.OlmEvent(
                        SecretKeyRequestEventContent(
                            M_CROSS_SIGNING_USER_SIGNING.id,
                            KeyRequestAction.REQUEST,
                            aliceDevice,
                            "requestId"
                        ),
                        alice, keysOf(), alice, keysOf()
                    )
                )
            )
            cut.handleEncryptedIncomingKeyRequests(
                OlmService.DecryptedOlmEvent(
                    mockk(), Event.OlmEvent(
                        SecretKeyRequestEventContent(
                            M_CROSS_SIGNING_USER_SIGNING.id,
                            KeyRequestAction.REQUEST_CANCELLATION,
                            aliceDevice,
                            "requestId"
                        ),
                        alice, keysOf(), alice, keysOf()
                    )
                )
            )
            cut.processIncomingKeyRequests()
            coVerify { api wasNot Called }
        }
    }
    context(KeyService::processIncomingKeyRequests.name) {
        beforeTest {
            store.account.userId.value = alice
            coEvery { api.users.sendToDevice<SecretKeySendEventContent>(any(), any()) } returns Result.success(Unit)
            store.keys.secrets.value =
                mapOf(M_CROSS_SIGNING_USER_SIGNING to StoredSecret(mockk(), "secretUserSigningKey"))
        }
        suspend fun ShouldSpecContainerScope.answerRequest(returnedTrustLevel: KeySignatureTrustLevel) {
            should("answer request with trust level $returnedTrustLevel") {
                store.keys.updateDeviceKeys(alice) {
                    mapOf(aliceDevice to mockk { every { trustLevel } returns returnedTrustLevel })
                }
                cut.handleEncryptedIncomingKeyRequests(
                    OlmService.DecryptedOlmEvent(
                        mockk(), Event.OlmEvent(
                            SecretKeyRequestEventContent(
                                M_CROSS_SIGNING_USER_SIGNING.id,
                                KeyRequestAction.REQUEST,
                                aliceDevice,
                                "requestId"
                            ),
                            alice, keysOf(), alice, keysOf()
                        )
                    )
                )
                cut.processIncomingKeyRequests()
                cut.processIncomingKeyRequests()
                coVerify(exactly = 1) {
                    api.users.sendToDevice(
                        mapOf(
                            alice to mapOf(
                                aliceDevice to SecretKeySendEventContent(
                                    "requestId",
                                    "secretUserSigningKey"
                                )
                            )
                        ), any(), any()
                    )
                }
            }
        }
        answerRequest(Valid(true))
        answerRequest(CrossSigned(true))
        suspend fun ShouldSpecContainerScope.notAnswerRequest(returnedTrustLevel: KeySignatureTrustLevel) {
            should("not answer request with trust level $returnedTrustLevel") {
                store.keys.updateDeviceKeys(alice) {
                    mapOf(aliceDevice to mockk { every { trustLevel } returns returnedTrustLevel })
                }
                cut.handleEncryptedIncomingKeyRequests(
                    OlmService.DecryptedOlmEvent(
                        mockk(), Event.OlmEvent(
                            SecretKeyRequestEventContent(
                                M_CROSS_SIGNING_USER_SIGNING.id,
                                KeyRequestAction.REQUEST,
                                aliceDevice,
                                "requestId"
                            ),
                            alice, keysOf(), alice, keysOf()
                        )
                    )
                )
                cut.processIncomingKeyRequests()
                cut.processIncomingKeyRequests()
                coVerify { api wasNot Called }
            }
        }
        notAnswerRequest(Valid(false))
        notAnswerRequest(CrossSigned(false))
        notAnswerRequest(NotCrossSigned)
        notAnswerRequest(Blocked)
        notAnswerRequest(Invalid("reason"))
    }
    context(KeyService::handleOutgoingKeyRequestAnswer.name) {
        val (publicKey, privateKey) = freeAfter(OlmPkSigning.create(null)) { it.publicKey to it.privateKey }
        val aliceDevice2Key = Key.Ed25519Key(aliceDevice, "aliceDevice2KeyValue")
        suspend fun setDeviceKeys(trusted: Boolean) {
            store.keys.updateDeviceKeys(alice) {
                mapOf(
                    aliceDevice to StoredDeviceKeys(
                        SignedDeviceKeys(DeviceKeys(alice, aliceDevice, setOf(), keysOf(aliceDevice2Key)), mapOf()),
                        CrossSigned(trusted)
                    )
                )
            }
        }

        suspend fun setRequest(receiverDeviceIds: Set<String>) {
            store.keys.addSecretKeyRequest(
                StoredSecretKeyRequest(
                    SecretKeyRequestEventContent(
                        M_CROSS_SIGNING_USER_SIGNING.id,
                        KeyRequestAction.REQUEST,
                        "OWN_ALICE_DEVICE",
                        "requestId"
                    ), receiverDeviceIds, Clock.System.now()
                )
            )
            store.keys.allSecretKeyRequests.first { it.size == 1 }
        }

        suspend fun setCrossSigningKeys(publicKey: String) {
            store.keys.updateCrossSigningKeys(alice) {
                setOf(
                    StoredCrossSigningKeys(
                        SignedCrossSigningKeys(
                            CrossSigningKeys(
                                alice, setOf(CrossSigningKeysUsage.UserSigningKey), keysOf(
                                    Key.Ed25519Key(publicKey, publicKey)
                                )
                            ), mapOf()
                        ), CrossSigned(true)
                    )
                )
            }
        }
        should("ignore, when sender device id cannot be found") {
            cut.handleOutgoingKeyRequestAnswer(
                OlmService.DecryptedOlmEvent(
                    mockk(), Event.OlmEvent(
                        SecretKeySendEventContent("requestId", privateKey),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            continually(500.milliseconds) {
                store.keys.secrets.value shouldBe mapOf()
            }
        }
        should("ignore when sender was not requested") {
            setDeviceKeys(true)
            setRequest(setOf("OTHER_DEVICE"))
            cut.handleOutgoingKeyRequestAnswer(
                OlmService.DecryptedOlmEvent(
                    mockk(), Event.OlmEvent(
                        SecretKeySendEventContent("requestId", privateKey),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            continually(500.milliseconds) {
                store.keys.secrets.value shouldBe mapOf()
            }
        }
        should("ignore when sender is not trusted") {
            setDeviceKeys(false)
            cut.handleOutgoingKeyRequestAnswer(
                OlmService.DecryptedOlmEvent(
                    mockk(), Event.OlmEvent(
                        SecretKeySendEventContent("requestId", privateKey),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            continually(500.milliseconds) {
                store.keys.secrets.value shouldBe mapOf()
            }
        }
        should("ignore when public key cannot be generated") {
            setDeviceKeys(true)
            setRequest(setOf(aliceDevice))
            setCrossSigningKeys(publicKey)
            cut.handleOutgoingKeyRequestAnswer(
                OlmService.DecryptedOlmEvent(
                    mockk(), Event.OlmEvent(
                        SecretKeySendEventContent("requestId", "dino"),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            continually(500.milliseconds) {
                store.keys.secrets.value shouldBe mapOf()
            }
        }
        should("ignore when public key does not match") {
            setDeviceKeys(true)
            setRequest(setOf(aliceDevice))
            setCrossSigningKeys(freeAfter(OlmPkSigning.create(null)) { it.publicKey })
            val secretEventContent = UserSigningKeyEventContent(mapOf())
            store.globalAccountData.update(GlobalAccountDataEvent(secretEventContent))
            cut.handleOutgoingKeyRequestAnswer(
                OlmService.DecryptedOlmEvent(
                    mockk(), Event.OlmEvent(
                        SecretKeySendEventContent("requestId", privateKey),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            continually(500.milliseconds) {
                store.keys.secrets.value shouldBe mapOf()
            }
        }
        should("ignore when encrypted secret could not be found") {
            setDeviceKeys(true)
            setRequest(setOf(aliceDevice))
            setCrossSigningKeys(publicKey)
            cut.handleOutgoingKeyRequestAnswer(
                OlmService.DecryptedOlmEvent(
                    mockk(), Event.OlmEvent(
                        SecretKeySendEventContent("requestId", privateKey),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            continually(500.milliseconds) {
                store.keys.secrets.value shouldBe mapOf()
            }
        }
        should("save secret") {
            setDeviceKeys(true)
            setRequest(setOf(aliceDevice))
            setCrossSigningKeys(publicKey)
            val secretEvent: GlobalAccountDataEvent<SecretEventContent> =
                GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf()))
            store.globalAccountData.update(secretEvent)
            cut.handleOutgoingKeyRequestAnswer(
                OlmService.DecryptedOlmEvent(
                    mockk(), Event.OlmEvent(
                        SecretKeySendEventContent("requestId", privateKey),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            store.keys.secrets.first { it.size == 1 } shouldBe mapOf(
                M_CROSS_SIGNING_USER_SIGNING to StoredSecret(secretEvent, privateKey)
            )
        }
        should("cancel other requests") {
            coEvery { api.users.sendToDevice<SecretKeyRequestEventContent>(any(), any()) } returns Result.success(Unit)
            setDeviceKeys(true)
            setRequest(setOf(aliceDevice, "OTHER_DEVICE"))
            setCrossSigningKeys(publicKey)
            val secretEvent: GlobalAccountDataEvent<SecretEventContent> =
                GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf()))
            store.globalAccountData.update(secretEvent)
            cut.handleOutgoingKeyRequestAnswer(
                OlmService.DecryptedOlmEvent(
                    mockk(), Event.OlmEvent(
                        SecretKeySendEventContent("requestId", privateKey),
                        alice, keysOf(aliceDevice2Key), alice, keysOf()
                    )
                )
            )
            store.keys.secrets.first { it.size == 1 } shouldBe mapOf(
                M_CROSS_SIGNING_USER_SIGNING to StoredSecret(secretEvent, privateKey)
            )
            coVerify {
                api.users.sendToDevice(
                    mapOf(
                        alice to mapOf(
                            "OTHER_DEVICE" to SecretKeyRequestEventContent(
                                M_CROSS_SIGNING_USER_SIGNING.id,
                                KeyRequestAction.REQUEST_CANCELLATION,
                                "OWN_ALICE_DEVICE",
                                "requestId"
                            )
                        )
                    ), any()
                )
            }
        }
    }
    context(KeyService::cancelOldOutgoingKeyRequests.name) {
        should("only remove old requests and send cancel") {
            coEvery { api.users.sendToDevice<SecretKeyRequestEventContent>(any(), any()) } returns Result.success(Unit)
            val request1 = StoredSecretKeyRequest(
                SecretKeyRequestEventContent(
                    M_CROSS_SIGNING_USER_SIGNING.id,
                    KeyRequestAction.REQUEST,
                    "OWN_ALICE_DEVICE",
                    "requestId1"
                ), setOf(), Clock.System.now()
            )
            val request2 = StoredSecretKeyRequest(
                SecretKeyRequestEventContent(
                    M_CROSS_SIGNING_USER_SIGNING.id,
                    KeyRequestAction.REQUEST,
                    "OWN_ALICE_DEVICE",
                    "requestId2"
                ), setOf(aliceDevice), (Clock.System.now() - 1.days)
            )
            store.keys.addSecretKeyRequest(request1)
            store.keys.addSecretKeyRequest(request2)
            store.keys.allSecretKeyRequests.first { it.size == 2 }

            cut.cancelOldOutgoingKeyRequests()

            store.keys.allSecretKeyRequests.first { it.size == 1 } shouldBe setOf(request1)
            coVerify {
                api.users.sendToDevice(
                    mapOf(
                        alice to mapOf(
                            aliceDevice to SecretKeyRequestEventContent(
                                M_CROSS_SIGNING_USER_SIGNING.id,
                                KeyRequestAction.REQUEST_CANCELLATION,
                                "OWN_ALICE_DEVICE",
                                "requestId2"
                            )
                        )
                    ), any()
                )
            }
        }
    }
    context(KeyService::requestSecretKeys.name) {
        should("ignore when there are no missing secrets") {
            store.keys.secrets.value = mapOf(
                M_CROSS_SIGNING_USER_SIGNING to StoredSecret(mockk(), "key1"),
                M_CROSS_SIGNING_SELF_SIGNING to StoredSecret(mockk(), "key2"),
                M_MEGOLM_BACKUP_V1 to StoredSecret(mockk(), "key3")
            )
            cut.requestSecretKeys()
            coVerify { api wasNot Called }
        }
        should("send requests to verified cross signed devices") {
            coEvery { api.users.sendToDevice<SecretKeyRequestEventContent>(any(), any()) } returns Result.success(Unit)
            store.keys.secrets.value = mapOf(
                M_MEGOLM_BACKUP_V1 to StoredSecret(mockk(), "key3")
            )
            store.keys.addSecretKeyRequest(
                StoredSecretKeyRequest(
                    SecretKeyRequestEventContent(
                        M_CROSS_SIGNING_SELF_SIGNING.id,
                        KeyRequestAction.REQUEST,
                        aliceDevice,
                        "requestId1"
                    ), setOf("DEVICE_2"), Clock.System.now()
                )
            )
            store.keys.allSecretKeyRequests.first { it.size == 1 }
            store.keys.updateDeviceKeys(alice) {
                mapOf(
                    "DEVICE_1" to StoredDeviceKeys(
                        SignedDeviceKeys(DeviceKeys(alice, "DEVICE_1", setOf(), keysOf()), mapOf()),
                        CrossSigned(false)
                    ),
                    "DEVICE_2" to StoredDeviceKeys(
                        SignedDeviceKeys(DeviceKeys(alice, "DEVICE_2", setOf(), keysOf()), mapOf()),
                        CrossSigned(true)
                    )
                )
            }
            cut.requestSecretKeys()
            coVerify {
                api.users.sendToDevice<SecretKeyRequestEventContent>(coWithArg { content ->
                    content shouldHaveSize 1
                    content[alice]?.size shouldBe 1
                    assertSoftly(content[alice]?.get("DEVICE_2")) {
                        assertNotNull(this)
                        this.name shouldBe M_CROSS_SIGNING_USER_SIGNING.id
                        this.action shouldBe KeyRequestAction.REQUEST
                        this.requestingDeviceId shouldBe aliceDevice
                        this.requestId shouldNot beEmpty()
                    }
                }, any())
            }
            store.keys.allSecretKeyRequests.first { it.size == 2 } shouldHaveSize 2
        }
    }
    context(KeyService::requestSecretKeysWhenCrossSigned.name) {
        should("request secret keys, when cross signed and verified") {
            coEvery {
                api.sync.currentSyncState
            } returns MutableStateFlow(SyncApiClient.SyncState.RUNNING)

            val spyCut = spyk(cut)
            coEvery { spyCut.requestSecretKeys() } just Runs
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                spyCut.requestSecretKeysWhenCrossSigned()
            }
            eventually(2.seconds, 500.milliseconds) {
                store.keys.updateDeviceKeys(alice) {
                    mapOf(
                        aliceDevice to StoredDeviceKeys(
                            SignedDeviceKeys(DeviceKeys(alice, aliceDevice, setOf(), keysOf()), mapOf()),
                            CrossSigned(false)
                        )
                    )
                }
                store.keys.updateDeviceKeys(alice) {
                    mapOf(
                        aliceDevice to StoredDeviceKeys(
                            SignedDeviceKeys(DeviceKeys(alice, aliceDevice, setOf(), keysOf()), mapOf()),
                            CrossSigned(true)
                        )
                    )
                }
                coVerify(timeout = 500) { spyCut.requestSecretKeys() }
            }
            job.cancel()
        }
    }
    context(KeyService::handleChangedSecrets.name) {
        beforeTest {
            coEvery { api.users.sendToDevice<SecretKeyRequestEventContent>(any(), any()) } returns Result.success(Unit)
            coEvery { api.eventContentSerializerMappings } returns DefaultEventContentSerializerMappings
            store.keys.addSecretKeyRequest(
                StoredSecretKeyRequest(
                    SecretKeyRequestEventContent(
                        M_CROSS_SIGNING_USER_SIGNING.id,
                        KeyRequestAction.REQUEST,
                        aliceDevice,
                        "requestId1"
                    ), setOf("DEVICE_2"), Clock.System.now()
                )
            )
            store.keys.allSecretKeyRequests.first { it.size == 1 }
        }
        should("do nothing when secret is not allowed to cache") {
            val crossSigningPrivateKeys = mapOf(M_CROSS_SIGNING_USER_SIGNING to mockk<StoredSecret>())
            store.keys.secrets.value = crossSigningPrivateKeys
            cut.handleChangedSecrets(GlobalAccountDataEvent(MasterKeyEventContent(mapOf())))
            coVerify(exactly = 0) { api.users.sendToDevice<SecretKeyRequestEventContent>(any(), any(), any()) }
            store.keys.secrets.value shouldBe crossSigningPrivateKeys
        }
        should("do nothing when event did not change") {
            val event = GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf()))
            val crossSigningPrivateKeys = mapOf(
                M_CROSS_SIGNING_USER_SIGNING to StoredSecret(
                    event, "bla"
                )
            )
            store.keys.secrets.value = crossSigningPrivateKeys
            cut.handleChangedSecrets(event)
            coVerify(exactly = 0) { api.users.sendToDevice<SecretKeyRequestEventContent>(any(), any(), any()) }
            store.keys.secrets.value shouldBe crossSigningPrivateKeys
        }
        should("remove cached secret and cancel ongoing requests when event did change") {
            store.keys.secrets.value = mapOf(
                M_CROSS_SIGNING_USER_SIGNING to StoredSecret(mockk(), "bla")
            )
            cut.handleChangedSecrets(GlobalAccountDataEvent(UserSigningKeyEventContent(mapOf())))
            coVerify {
                api.users.sendToDevice<SecretKeyRequestEventContent>(coWithArg { content ->
                    content shouldHaveSize 1
                    content[alice]?.size shouldBe 1
                    assertSoftly(content[alice]?.get("DEVICE_2")) {
                        assertNotNull(this)
                        this.name shouldBe M_CROSS_SIGNING_USER_SIGNING.id
                        this.action shouldBe KeyRequestAction.REQUEST_CANCELLATION
                        this.requestingDeviceId shouldBe aliceDevice
                        this.requestId shouldBe "requestId1"
                    }
                }, any())
            }
            store.keys.secrets.value shouldBe mapOf()
        }
    }
    context(KeyService::decryptSecret.name) {
        should("decrypt ${SecretKeyEventContent.AesHmacSha2Key::class.simpleName}") {
            val key = Random.nextBytes(32)
            val secret = Random.nextBytes(32).encodeBase64()
            val encryptedData = encryptAesHmacSha2(
                content = secret.encodeToByteArray(),
                key = key,
                name = "m.cross_signing.user_signing"
            )
            cut.decryptSecret(
                key = key,
                keyId = "KEY",
                keyInfo = SecretKeyEventContent.AesHmacSha2Key(),
                secretName = "m.cross_signing.user_signing",
                secret = UserSigningKeyEventContent(mapOf("KEY" to json.encodeToJsonElement(encryptedData)))
            ) shouldBe secret
        }
        should("return null on error") {
            val secret = Random.nextBytes(32)
            val encryptedData = encryptAesHmacSha2(
                content = secret,
                key = Random.nextBytes(32),
                name = "m.cross_signing.user_signing"
            )
            cut.decryptSecret(
                key = Random.nextBytes(32),
                keyId = "KEY",
                keyInfo = SecretKeyEventContent.AesHmacSha2Key(),
                secretName = "m.cross_signing.user_signing",
                secret = UserSigningKeyEventContent(mapOf("KEY" to json.encodeToJsonElement(encryptedData)))
            ) shouldBe null
        }
    }
    context(KeyService::decryptMissingSecrets.name) {
        should("decrypt missing secrets and update secure store") {
            val existingPrivateKeys = mapOf(
                M_CROSS_SIGNING_SELF_SIGNING to StoredSecret(
                    GlobalAccountDataEvent(SelfSigningKeyEventContent(mapOf())), "key2"
                ),
                M_MEGOLM_BACKUP_V1 to StoredSecret(
                    GlobalAccountDataEvent(SelfSigningKeyEventContent(mapOf())), "key3"
                )
            )
            store.keys.secrets.value = existingPrivateKeys

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
            store.globalAccountData.update(event)

            cut.decryptMissingSecrets(key, "KEY", SecretKeyEventContent.AesHmacSha2Key())
            store.keys.secrets.value shouldBe existingPrivateKeys + mapOf(
                M_CROSS_SIGNING_USER_SIGNING to StoredSecret(event, secret),
            )
        }
    }
    context(KeyService::checkOwnAdvertisedMasterKeyAndVerifySelf.name) {
        lateinit var spyCut: KeyService
        beforeTest {
            spyCut = spyk(cut)
            coEvery { spyCut.trustAndSignKeys(any(), any()) } just Runs
        }
        should("fail when master key cannot be found") {
            spyCut.checkOwnAdvertisedMasterKeyAndVerifySelf(ByteArray(32), "keyId", mockk()).isFailure shouldBe true
        }
        should("fail when master key does not match") {
            val encryptedMasterKey = MasterKeyEventContent(mapOf())
            store.globalAccountData.update(GlobalAccountDataEvent(encryptedMasterKey))
            val publicKey = Random.nextBytes(32).encodeUnpaddedBase64()
            store.keys.updateCrossSigningKeys(alice) {
                setOf(
                    StoredCrossSigningKeys(
                        SignedCrossSigningKeys(
                            CrossSigningKeys(
                                alice, setOf(CrossSigningKeysUsage.UserSigningKey), keysOf(
                                    Key.Ed25519Key(publicKey, publicKey)
                                )
                            ), mapOf()
                        ), CrossSigned(true)
                    )
                )
            }

            coEvery { spyCut.decryptSecret(any(), any(), any(), any(), any()) } returns Random.nextBytes(32)
                .encodeBase64()

            spyCut.checkOwnAdvertisedMasterKeyAndVerifySelf(ByteArray(32), "keyId", mockk()).isFailure shouldBe true
        }
        should("be success, when master key matches") {
            val encryptedMasterKey = MasterKeyEventContent(mapOf())
            store.globalAccountData.update(GlobalAccountDataEvent(encryptedMasterKey))
            val privateKey = Random.nextBytes(32).encodeBase64()
            val publicKey = freeAfter(OlmPkSigning.create(privateKey)) { it.publicKey }
            store.keys.updateCrossSigningKeys(alice) {
                setOf(
                    StoredCrossSigningKeys(
                        SignedCrossSigningKeys(
                            CrossSigningKeys(
                                alice, setOf(CrossSigningKeysUsage.MasterKey), keysOf(
                                    Key.Ed25519Key(publicKey, publicKey)
                                )
                            ), mapOf()
                        ), Valid(false)
                    )
                )
            }
            store.keys.updateDeviceKeys(alice) {
                mapOf(
                    aliceDevice to StoredDeviceKeys(
                        SignedDeviceKeys(
                            DeviceKeys(
                                alice, aliceDevice, setOf(),
                                keysOf(Key.Ed25519Key(aliceDevice, "dev"))
                            ), mapOf()
                        ),
                        Valid(false)
                    )
                )
            }

            coEvery { spyCut.decryptSecret(any(), any(), any(), any(), any()) } returns privateKey

            spyCut.checkOwnAdvertisedMasterKeyAndVerifySelf(ByteArray(32), "keyId", mockk()).getOrThrow()

            coVerify {
                spyCut.trustAndSignKeys(
                    setOf(
                        Key.Ed25519Key(publicKey, publicKey),
                        Key.Ed25519Key(aliceDevice, "dev")
                    ), alice
                )
            }
        }
    }
    context(KeyService::bootstrapCrossSigning.name) {
        context("successfull") {
            lateinit var spyCut: KeyService
            beforeTest {
                spyCut = spyk(cut)

                coEvery { api.json } returns createMatrixJson()
                coEvery { api.users.setAccountData<SecretKeyEventContent>(any(), any(), any()) }
                    .returns(Result.success(Unit))
                coEvery { api.users.setAccountData<DefaultSecretKeyEventContent>(any(), any()) }
                    .returns(Result.success(Unit))
                coEvery { api.users.setAccountData<MasterKeyEventContent>(any(), any()) }
                    .returns(Result.success(Unit))
                coEvery { api.users.setAccountData<SelfSigningKeyEventContent>(any(), any()) }
                    .returns(Result.success(Unit))
                coEvery { api.users.setAccountData<UserSigningKeyEventContent>(any(), any()) }
                    .returns(Result.success(Unit))
                coEvery { olm.sign.sign(any<CrossSigningKeys>(), any<OlmSignService.SignWith>()) }.answers {
                    Signed(firstArg(), mapOf())
                }
                coEvery { api.keys.setCrossSigningKeys(any(), any(), any()) }
                    .returns(Result.success(UIA.UIASuccess(Unit)))
                coEvery { spyCut.trustAndSignKeys(any(), any()) } just Runs
                store.keys.updateCrossSigningKeys(alice) {
                    setOf(
                        StoredCrossSigningKeys(
                            SignedCrossSigningKeys(
                                CrossSigningKeys(
                                    alice, setOf(CrossSigningKeysUsage.MasterKey), keysOf(
                                        Key.Ed25519Key("A_MSK", "A_MSK")
                                    )
                                ), mapOf()
                            ), Valid(false)
                        )
                    )
                }
                store.keys.updateDeviceKeys(alice) {
                    mapOf(
                        aliceDevice to StoredDeviceKeys(
                            SignedDeviceKeys(
                                DeviceKeys(
                                    alice, aliceDevice, setOf(),
                                    keysOf(Key.Ed25519Key(aliceDevice, "dev"))
                                ), mapOf()
                            ),
                            Valid(false)
                        )
                    )
                }
            }
            should("bootstrap") {
                val result = async { spyCut.bootstrapCrossSigning() }
                store.keys.outdatedKeys.first { it.contains(alice) }
                store.keys.outdatedKeys.value = setOf()

                assertSoftly(result.await()) {
                    this.recoveryKey shouldNot beEmpty()
                    this.result shouldBe Result.success(UIA.UIASuccess(Unit))
                }
                coVerify {
                    api.users.setAccountData<SecretKeyEventContent>(
                        content = coWithArg {
                            it.shouldBeInstanceOf<SecretKeyEventContent.AesHmacSha2Key>()
                            it.iv shouldNot beEmpty()
                            it.mac shouldNot beEmpty()
                            it.passphrase shouldBe null
                        },
                        userId = alice,
                        key = coWithArg { it.length shouldBeGreaterThan 10 }
                    )
                    api.users.setAccountData<DefaultSecretKeyEventContent>(
                        content = coWithArg { it.key.length shouldBeGreaterThan 10 },
                        userId = alice
                    )
                    api.users.setAccountData<MasterKeyEventContent>(
                        content = coWithArg {
                            val encrypted = it.encrypted.values.first()
                            encrypted.shouldBeInstanceOf<JsonObject>()
                            encrypted["iv"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                            encrypted["mac"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                        },
                        userId = alice
                    )
                    api.users.setAccountData<SelfSigningKeyEventContent>(
                        content = coWithArg {
                            val encrypted = it.encrypted.values.first()
                            encrypted.shouldBeInstanceOf<JsonObject>()
                            encrypted["iv"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                            encrypted["mac"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                        },
                        userId = alice
                    )
                    api.users.setAccountData<UserSigningKeyEventContent>(
                        content = coWithArg {
                            val encrypted = it.encrypted.values.first()
                            encrypted.shouldBeInstanceOf<JsonObject>()
                            encrypted["iv"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                            encrypted["mac"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                        },
                        userId = alice
                    )
                    api.keys.setCrossSigningKeys(any(), any(), any())
                    spyCut.trustAndSignKeys(
                        setOf(
                            Key.Ed25519Key("A_MSK", "A_MSK"),
                            Key.Ed25519Key(aliceDevice, "dev")
                        ), alice
                    )
                }
                store.keys.secrets.value.keys shouldBe setOf(M_CROSS_SIGNING_SELF_SIGNING, M_CROSS_SIGNING_USER_SIGNING)
            }
            should("bootstrap from passphrase") {
                val result = async { spyCut.bootstrapCrossSigningFromPassphrase("super secret. not.") }
                store.keys.outdatedKeys.first { it.contains(alice) }
                store.keys.outdatedKeys.value = setOf()

                assertSoftly(result.await()) {
                    this.recoveryKey shouldNot beEmpty()
                    this.result shouldBe Result.success(UIA.UIASuccess(Unit))
                }
                coVerify {
                    api.users.setAccountData<SecretKeyEventContent>(
                        content = coWithArg {
                            it.shouldBeInstanceOf<SecretKeyEventContent.AesHmacSha2Key>()
                            it.iv shouldNot beEmpty()
                            it.mac shouldNot beEmpty()
                            assertSoftly(it.passphrase) {
                                this.shouldBeInstanceOf<Pbkdf2>()
                                this.bits shouldBe 32 * 8
                                this.iterations shouldBeGreaterThanOrEqual 500_000
                                this.salt shouldNot beEmpty()
                            }
                        },
                        userId = alice,
                        key = coWithArg { it.length shouldBeGreaterThan 10 }
                    )
                    api.users.setAccountData<DefaultSecretKeyEventContent>(
                        content = coWithArg { it.key.length shouldBeGreaterThan 10 },
                        userId = alice
                    )
                    api.users.setAccountData<MasterKeyEventContent>(
                        content = coWithArg {
                            val encrypted = it.encrypted.values.first()
                            encrypted.shouldBeInstanceOf<JsonObject>()
                            encrypted["iv"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                            encrypted["mac"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                        },
                        userId = alice
                    )
                    api.users.setAccountData<SelfSigningKeyEventContent>(
                        content = coWithArg {
                            val encrypted = it.encrypted.values.first()
                            encrypted.shouldBeInstanceOf<JsonObject>()
                            encrypted["iv"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                            encrypted["mac"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                        },
                        userId = alice
                    )
                    api.users.setAccountData<UserSigningKeyEventContent>(
                        content = coWithArg {
                            val encrypted = it.encrypted.values.first()
                            encrypted.shouldBeInstanceOf<JsonObject>()
                            encrypted["iv"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                            encrypted["mac"].shouldBeInstanceOf<JsonPrimitive>().content shouldNot beEmpty()
                        },
                        userId = alice
                    )
                    api.keys.setCrossSigningKeys(any(), any(), any())
                    spyCut.trustAndSignKeys(
                        setOf(
                            Key.Ed25519Key("A_MSK", "A_MSK"),
                            Key.Ed25519Key(aliceDevice, "dev")
                        ), alice
                    )
                }
                store.keys.secrets.value.keys shouldBe setOf(M_CROSS_SIGNING_SELF_SIGNING, M_CROSS_SIGNING_USER_SIGNING)
            }
        }
    }
}