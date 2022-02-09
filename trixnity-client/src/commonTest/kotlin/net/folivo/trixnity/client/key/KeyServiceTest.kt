package net.folivo.trixnity.client.key

import io.kotest.assertions.timing.continually
import io.kotest.assertions.until.fixed
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.ktor.util.*
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.SyncApiClient
import net.folivo.trixnity.client.api.model.keys.QueryKeysResponse
import net.folivo.trixnity.client.api.model.sync.SyncResponse
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.*
import net.folivo.trixnity.client.crypto.OlmService
import net.folivo.trixnity.client.crypto.VerifyResult
import net.folivo.trixnity.client.crypto.getCrossSigningKey
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage.*
import net.folivo.trixnity.core.model.keys.Key.Ed25519Key
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds

class KeyServiceTest : ShouldSpec(body)

@OptIn(InternalAPI::class)
private val body: ShouldSpec.() -> Unit = {
    timeout = 15_000

    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val aliceDevice = "ALICEDEVICE"
    val bobDevice = "BOBDEVICE"
    lateinit var scope: CoroutineScope
    lateinit var store: Store
    val olm = mockk<OlmService>()
    val api = mockk<MatrixApiClient>()
    val trust = mockk<KeyTrustService>(relaxUnitFun = true)

    lateinit var cut: KeyService

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(scope).apply { init() }
        cut = KeyService("", alice, aliceDevice, store, olm, api, trust = trust)
        coEvery { olm.sign.verify(any<SignedDeviceKeys>(), any()) } returns VerifyResult.Valid
        coEvery { olm.sign.verify(any<SignedCrossSigningKeys>(), any()) } returns VerifyResult.Valid
        coEvery { trust.calculateCrossSigningKeysTrustLevel(any()) } returns CrossSigned(false)
        coEvery { trust.calculateDeviceKeysTrustLevel(any()) } returns CrossSigned(false)
    }

    afterTest {
        clearAllMocks()
        scope.cancel()
    }

    context(KeyService::handleDeviceLists.name) {
        context("device key is tracked") {
            should("add changed devices to outdated keys") {
                store.keys.outdatedKeys.value = setOf(alice)
                store.keys.updateDeviceKeys(bob) { mapOf(bobDevice to mockk()) }
                cut.handleDeviceLists(SyncResponse.DeviceLists(changed = setOf(bob)))
                store.keys.outdatedKeys.value shouldContainExactly setOf(alice, bob)
            }
            should("remove key when user left") {
                store.keys.outdatedKeys.value = setOf(alice, bob)
                store.keys.updateDeviceKeys(alice) { mockk() }
                cut.handleDeviceLists(SyncResponse.DeviceLists(left = setOf(alice)))
                store.keys.getDeviceKeys(alice) should beNull()
                store.keys.outdatedKeys.value shouldContainExactly setOf(bob)
            }
        }
        context("device key is not tracked") {
            should("not add changed devices to outdated keys") {
                store.keys.outdatedKeys.value = setOf(alice)
                cut.handleDeviceLists(SyncResponse.DeviceLists(changed = setOf(bob)))
                store.keys.outdatedKeys.value shouldContainExactly setOf(alice)
            }
        }
    }

    context(KeyService::handleOutdatedKeys.name) {
        val cedric = UserId("cedric", "server")
        val cedricDevice = "CEDRIC_DEVICE"
        val cedricKey1 = Signed<DeviceKeys, UserId>(
            DeviceKeys(cedric, cedricDevice, setOf(), keysOf(Ed25519Key("id", "value"))), mapOf()
        )
        val aliceDevice2 = "ALICE_DEVICE_2"
        val aliceKey2 = Signed<DeviceKeys, UserId>(
            DeviceKeys(alice, aliceDevice2, setOf(), keysOf(Ed25519Key("id", "value"))), mapOf()
        )
        beforeTest {
            coEvery { api.sync.currentSyncState } returns MutableStateFlow(SyncApiClient.SyncState.RUNNING)
            scope.launch {
                cut.handleOutdatedKeys()
            }
        }
        should("do nothing when no keys outdated") {
            store.keys.outdatedKeys.value = setOf()
            continually(500.milliseconds, 50.milliseconds.fixed()) {
                verify { api.keys wasNot Called }
            }
        }
        context("master keys") {
            should("allow missing signature") {
                val key = Signed<CrossSigningKeys, UserId>(
                    CrossSigningKeys(alice, setOf(MasterKey), keysOf(Ed25519Key("id", "value"))), mapOf()
                )
                coEvery { olm.sign.verify(key, any()) } returns VerifyResult.MissingSignature("")
                coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns Result.success(
                    QueryKeysResponse(
                        mapOf(), mapOf(),
                        mapOf(alice to key),
                        mapOf(), mapOf()
                    )
                )
                store.keys.outdatedKeys.value = setOf(alice)
                store.keys.outdatedKeys.first { it.isEmpty() }
                store.keys.getCrossSigningKeys(alice) shouldContainExactly setOf(
                    StoredCrossSigningKeys(key, CrossSigned(false))
                )
                coVerify { trust.updateTrustLevelOfKeyChainSignedBy(alice, Ed25519Key("id", "value")) }
            }
            should("ignore when signatures are invalid") {
                val invalidKey = Signed(
                    CrossSigningKeys(alice, setOf(MasterKey), keysOf(Ed25519Key("id", "value"))),
                    mapOf(alice to keysOf(Ed25519Key("invalid", "invalid")))
                )
                coEvery { olm.sign.verify(invalidKey, any()) } returns VerifyResult.Invalid("")
                coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns Result.success(
                    QueryKeysResponse(
                        mapOf(), mapOf(),
                        mapOf(alice to invalidKey),
                        mapOf(), mapOf()
                    )
                )
                store.keys.outdatedKeys.value = setOf(alice)
                store.keys.outdatedKeys.first { it.isEmpty() }
                store.keys.getCrossSigningKeys(alice).shouldBeNull()
                coVerify(exactly = 0) { trust.updateTrustLevelOfKeyChainSignedBy(any(), any()) }
            }
            should("add master key") {
                val key = Signed<CrossSigningKeys, UserId>(
                    CrossSigningKeys(alice, setOf(MasterKey), keysOf(Ed25519Key("id", "value"))), mapOf()
                )
                coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns Result.success(
                    QueryKeysResponse(
                        mapOf(), mapOf(),
                        mapOf(alice to key),
                        mapOf(), mapOf()
                    )
                )
                store.keys.outdatedKeys.value = setOf(alice)
                store.keys.outdatedKeys.first { it.isEmpty() }
                store.keys.getCrossSigningKeys(alice) shouldContainExactly setOf(
                    StoredCrossSigningKeys(key, CrossSigned(false))
                )
                coVerify { trust.updateTrustLevelOfKeyChainSignedBy(alice, Ed25519Key("id", "value")) }
            }
        }
        context("self signing keys") {
            should("ignore when signatures are invalid") {
                val invalidKey = Signed(
                    CrossSigningKeys(alice, setOf(SelfSigningKey), keysOf(Ed25519Key("id", "value"))),
                    mapOf(alice to keysOf(Ed25519Key("invalid", "invalid")))
                )
                coEvery { olm.sign.verify(invalidKey, any()) } returns VerifyResult.Invalid("")
                coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns Result.success(
                    QueryKeysResponse(
                        mapOf(), mapOf(), mapOf(),
                        mapOf(alice to invalidKey),
                        mapOf()
                    )
                )
                store.keys.outdatedKeys.value = setOf(alice)
                store.keys.outdatedKeys.first { it.isEmpty() }
                store.keys.getCrossSigningKeys(alice).shouldBeNull()
                coVerify(exactly = 0) { trust.updateTrustLevelOfKeyChainSignedBy(any(), any()) }
            }
            should("add self signing key") {
                val key = Signed<CrossSigningKeys, UserId>(
                    CrossSigningKeys(alice, setOf(SelfSigningKey), keysOf(Ed25519Key("id", "value"))), mapOf()
                )
                coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns Result.success(
                    QueryKeysResponse(
                        mapOf(), mapOf(), mapOf(),
                        mapOf(alice to key),
                        mapOf()
                    )
                )
                store.keys.outdatedKeys.value = setOf(alice)
                store.keys.outdatedKeys.first { it.isEmpty() }
                store.keys.getCrossSigningKeys(alice) shouldContainExactly setOf(
                    StoredCrossSigningKeys(key, CrossSigned(false))
                )
                coVerify { trust.updateTrustLevelOfKeyChainSignedBy(alice, Ed25519Key("id", "value")) }
            }
            should("replace self signing key") {
                val key = Signed<CrossSigningKeys, UserId>(
                    CrossSigningKeys(alice, setOf(SelfSigningKey), keysOf(Ed25519Key("id", "value"))), mapOf()
                )
                store.keys.updateCrossSigningKeys(alice) {
                    setOf(
                        StoredCrossSigningKeys(
                            Signed(key.signed, mapOf(alice to keysOf())),
                            Valid(true)
                        )
                    )
                }
                coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns Result.success(
                    QueryKeysResponse(
                        mapOf(), mapOf(), mapOf(),
                        mapOf(alice to key),
                        mapOf()
                    )
                )
                store.keys.outdatedKeys.value = setOf(alice)
                store.keys.outdatedKeys.first { it.isEmpty() }
                store.keys.getCrossSigningKeys(alice) shouldContainExactly setOf(
                    StoredCrossSigningKeys(key, CrossSigned(false))
                )
                coVerify { trust.updateTrustLevelOfKeyChainSignedBy(alice, Ed25519Key("id", "value")) }
            }
        }
        context("user signing keys") {
            should("ignore when signatures are invalid") {
                val invalidKey = Signed(
                    CrossSigningKeys(alice, setOf(UserSigningKey), keysOf(Ed25519Key("id", "value"))),
                    mapOf(alice to keysOf(Ed25519Key("invalid", "invalid")))
                )
                coEvery { olm.sign.verify(invalidKey, any()) } returns VerifyResult.Invalid("")
                coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns Result.success(
                    QueryKeysResponse(
                        mapOf(), mapOf(), mapOf(), mapOf(),
                        mapOf(alice to invalidKey)
                    )
                )
                store.keys.outdatedKeys.value = setOf(alice)
                continually(500.milliseconds, 50.milliseconds.fixed()) {
                    store.keys.getCrossSigningKeys(alice).shouldBeNull()
                }
                coVerify(exactly = 0) { trust.updateTrustLevelOfKeyChainSignedBy(any(), any()) }
            }
            should("add user signing key") {
                val key = Signed<CrossSigningKeys, UserId>(
                    CrossSigningKeys(alice, setOf(UserSigningKey), keysOf(Ed25519Key("id", "value"))), mapOf()
                )
                coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns Result.success(
                    QueryKeysResponse(
                        mapOf(), mapOf(), mapOf(), mapOf(),
                        mapOf(alice to key),
                    )
                )
                store.keys.outdatedKeys.value = setOf(alice)
                store.keys.outdatedKeys.first { it.isEmpty() }
                store.keys.getCrossSigningKeys(alice) shouldContainExactly setOf(
                    StoredCrossSigningKeys(key, CrossSigned(false))
                )
                coVerify { trust.updateTrustLevelOfKeyChainSignedBy(alice, Ed25519Key("id", "value")) }
            }
            should("replace user signing key") {
                val key = Signed<CrossSigningKeys, UserId>(
                    CrossSigningKeys(alice, setOf(UserSigningKey), keysOf(Ed25519Key("id", "value"))), mapOf()
                )
                store.keys.updateCrossSigningKeys(alice) {
                    setOf(
                        StoredCrossSigningKeys(
                            Signed(key.signed, mapOf(alice to keysOf())),
                            Valid(true)
                        )
                    )
                }
                coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns Result.success(
                    QueryKeysResponse(
                        mapOf(), mapOf(), mapOf(), mapOf(),
                        mapOf(alice to key),
                    )
                )
                store.keys.outdatedKeys.value = setOf(alice)
                store.keys.outdatedKeys.first { it.isEmpty() }
                store.keys.getCrossSigningKeys(alice) shouldContainExactly setOf(
                    StoredCrossSigningKeys(key, CrossSigned(false))
                )
                coVerify { trust.updateTrustLevelOfKeyChainSignedBy(alice, Ed25519Key("id", "value")) }
            }
        }
        context("device keys") {
            should("update outdated device keys") {
                coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns Result.success(
                    QueryKeysResponse(
                        mapOf(),
                        mapOf(
                            cedric to mapOf(cedricDevice to cedricKey1),
                            alice to mapOf(aliceDevice2 to aliceKey2)
                        ),
                        mapOf(), mapOf(), mapOf()
                    )
                ) andThen Result.success(
                    QueryKeysResponse(
                        mapOf(),
                        mapOf(alice to mapOf()),
                        mapOf(), mapOf(), mapOf()
                    )
                )
                store.keys.outdatedKeys.value = setOf(cedric, alice)
                store.keys.outdatedKeys.first { it.isEmpty() }
                val storedCedricKeys = store.keys.getDeviceKeys(cedric)
                assertNotNull(storedCedricKeys)
                storedCedricKeys shouldContainExactly mapOf(
                    cedricDevice to StoredDeviceKeys(
                        cedricKey1,
                        CrossSigned(false)
                    )
                )
                val storedAliceKeys = store.keys.getDeviceKeys(alice)
                assertNotNull(storedAliceKeys)
                storedAliceKeys shouldContainExactly mapOf(
                    aliceDevice2 to StoredDeviceKeys(
                        aliceKey2,
                        CrossSigned(false)
                    )
                )

                // check delete of device keys
                store.keys.outdatedKeys.value = setOf(alice)
                store.keys.outdatedKeys.first { it.isEmpty() }
                val storedAliceKeysAfterDelete = store.keys.getDeviceKeys(alice)
                assertNotNull(storedAliceKeysAfterDelete)
                storedAliceKeysAfterDelete shouldContainExactly mapOf()
            }
            should("look for encrypted room, where the user participates and notify megolm sessions about new device keys") {
                coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns Result.success(
                    QueryKeysResponse(
                        mapOf(),
                        mapOf(
                            cedric to mapOf(cedricDevice to cedricKey1),
                            alice to mapOf(aliceDevice2 to aliceKey2)
                        ),
                        mapOf(), mapOf(), mapOf()
                    )
                )
                val room1 = RoomId("room1", "server")
                val room2 = RoomId("room2", "server")
                val room3 = RoomId("room3", "server")
                store.room.update(room1) {
                    simpleRoom.copy(
                        roomId = room1,
                        encryptionAlgorithm = EncryptionAlgorithm.Megolm
                    )
                }
                store.room.update(room2) {
                    simpleRoom.copy(
                        roomId = room2,
                        encryptionAlgorithm = EncryptionAlgorithm.Megolm
                    )
                }
                store.room.update(room3) {
                    simpleRoom.copy(
                        roomId = room3,
                        encryptionAlgorithm = EncryptionAlgorithm.Megolm
                    )
                }
                listOf(
                    Event.StateEvent(
                        MemberEventContent(membership = MemberEventContent.Membership.JOIN),
                        EventId("\$event1"),
                        alice,
                        room1,
                        1234,
                        stateKey = alice.full
                    ),
                    Event.StateEvent(
                        MemberEventContent(membership = MemberEventContent.Membership.JOIN),
                        EventId("\$event2"),
                        alice,
                        room2,
                        1234,
                        stateKey = alice.full
                    ),
                    Event.StateEvent(
                        MemberEventContent(membership = MemberEventContent.Membership.JOIN),
                        EventId("\$event3"),
                        alice,
                        room3,
                        1234,
                        stateKey = alice.full
                    ),
                    Event.StateEvent(
                        MemberEventContent(membership = MemberEventContent.Membership.JOIN),
                        EventId("\$event4"),
                        cedric,
                        room1,
                        1234,
                        stateKey = cedric.full
                    ),
                ).forEach { store.roomState.update(it) }

                store.olm.updateOutboundMegolmSession(room1) { StoredOutboundMegolmSession(room1, pickled = "") }
                store.olm.updateOutboundMegolmSession(room3) {
                    StoredOutboundMegolmSession(
                        room3,
                        newDevices = mapOf(cedric to setOf(cedricDevice)),
                        pickled = ""
                    )
                }

                store.keys.outdatedKeys.value = setOf(cedric, alice)
                store.keys.outdatedKeys.first { it.isEmpty() }
                store.olm.getOutboundMegolmSession(room1)?.newDevices shouldBe mapOf(
                    alice to setOf(aliceDevice2),
                    cedric to setOf(cedricDevice)
                )
                store.olm.getOutboundMegolmSession(room2) should beNull()
                store.olm.getOutboundMegolmSession(room3)?.newDevices shouldBe mapOf(
                    alice to setOf(aliceDevice2),
                    cedric to setOf(cedricDevice)
                )
            }
            context("master key is present") {
                context("at least one device key is not cross signed") {
                    context("mark master key as ${NotAllDeviceKeysCrossSigned::class.simpleName}") {
                        withData(
                            CrossSigned(true) to true,
                            CrossSigned(false) to false,
                        ) { (levelBefore, expectedVerified) ->
                            coEvery { trust.calculateDeviceKeysTrustLevel(aliceKey2) } returns NotCrossSigned
                            coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns Result.success(
                                QueryKeysResponse(
                                    mapOf(),
                                    mapOf(alice to mapOf(aliceDevice2 to aliceKey2)),
                                    mapOf(), mapOf(), mapOf()
                                )
                            )
                            store.keys.updateCrossSigningKeys(alice) {
                                setOf(
                                    StoredCrossSigningKeys(
                                        Signed(
                                            CrossSigningKeys(
                                                alice,
                                                setOf(MasterKey),
                                                keysOf(Ed25519Key("mk_id", "mk_value"))
                                            ),
                                            mapOf()
                                        ), levelBefore
                                    )
                                )
                            }
                            store.keys.outdatedKeys.value = setOf(alice)
                            store.keys.outdatedKeys.first { it.isEmpty() }
                            store.keys.getCrossSigningKey(alice, MasterKey)
                                ?.trustLevel shouldBe NotAllDeviceKeysCrossSigned(expectedVerified)
                        }
                    }
                    context("keep trust level") {
                        withData(
                            Valid(false),
                            Valid(true),
                            NotAllDeviceKeysCrossSigned(true),
                            NotAllDeviceKeysCrossSigned(false),
                            NotCrossSigned,
                            Invalid(""),
                            Blocked
                        ) { levelBefore ->
                            coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns Result.success(
                                QueryKeysResponse(
                                    mapOf(),
                                    mapOf(alice to mapOf(aliceDevice2 to aliceKey2)),
                                    mapOf(), mapOf(), mapOf()
                                )
                            )
                            store.keys.updateCrossSigningKeys(alice) {
                                setOf(
                                    StoredCrossSigningKeys(
                                        Signed(
                                            CrossSigningKeys(
                                                alice,
                                                setOf(MasterKey),
                                                keysOf(Ed25519Key("id", "value"))
                                            ),
                                            mapOf()
                                        ), levelBefore
                                    )
                                )
                            }
                            store.keys.outdatedKeys.value = setOf(alice)
                            store.keys.outdatedKeys.first { it.isEmpty() }
                            store.keys.getCrossSigningKey(alice, MasterKey)
                                ?.trustLevel shouldBe levelBefore
                        }
                    }
                }
                context("all device keys are cross signed") {
                    val aliceMasterKey = Signed<CrossSigningKeys, UserId>(
                        CrossSigningKeys(
                            alice, setOf(MasterKey), keysOf(Ed25519Key("ALICE_MSK", "..."))
                        ),
                        mapOf()
                    )
                    beforeTest {
                        coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns Result.success(
                            QueryKeysResponse(
                                mapOf(),
                                mapOf(
                                    alice to mapOf(
                                        aliceDevice2 to Signed(
                                            aliceKey2.signed,
                                            mapOf(alice to keysOf(Ed25519Key("ALICE_MSK", "...")))
                                        )
                                    )
                                ),
                                mapOf(), mapOf(), mapOf()
                            )
                        )
                    }
                    should("do nothing when his trust level is ${CrossSigned::class.simpleName}") {
                        store.keys.updateCrossSigningKeys(alice) {
                            setOf(StoredCrossSigningKeys(aliceMasterKey, CrossSigned(true)))
                        }
                        store.keys.outdatedKeys.value = setOf(alice)
                        store.keys.outdatedKeys.first { it.isEmpty() }
                        store.keys.getCrossSigningKey(alice, MasterKey)
                            ?.trustLevel shouldBe CrossSigned(true)
                    }
                }
            }
            context("manipulation of ") {
                should("signature") {
                    coEvery { olm.sign.verify(cedricKey1, any()) } returns VerifyResult.Invalid("")
                    coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns Result.success(
                        QueryKeysResponse(
                            mapOf(), mapOf(cedric to mapOf(cedricDevice to cedricKey1)), mapOf(), mapOf(), mapOf()
                        )
                    )
                    store.keys.outdatedKeys.value = setOf(cedric)
                    store.keys.outdatedKeys.first { it.isEmpty() }
                    val storedKeys = store.keys.getDeviceKeys(cedric)
                    assertNotNull(storedKeys)
                    storedKeys shouldHaveSize 0
                }
                withData<Map<UserId, Map<String, Signed<DeviceKeys, UserId>>>>(
                    mapOf(
                        "userId" to mapOf(alice to mapOf(cedricDevice to cedricKey1)),
                        "deviceId" to mapOf(alice to mapOf(cedricDevice to aliceKey2)),
                    )
                ) { deviceKeys ->
                    coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns Result.success(
                        QueryKeysResponse(
                            mapOf(), deviceKeys, mapOf(), mapOf(), mapOf()
                        )
                    )
                    store.keys.outdatedKeys.value = setOf(alice, cedric)
                    store.keys.outdatedKeys.first { it.isEmpty() }
                    val storedKeys = store.keys.getDeviceKeys(alice)
                    assertNotNull(storedKeys)
                    storedKeys shouldHaveSize 0
                }
            }
        }
    }
}