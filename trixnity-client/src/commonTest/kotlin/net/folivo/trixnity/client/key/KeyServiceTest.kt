package net.folivo.trixnity.client.key

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.folivo.trixnity.client.api.MatrixApiClient
import net.folivo.trixnity.client.api.keys.QueryKeysResponse
import net.folivo.trixnity.client.api.sync.SyncResponse
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel.*
import net.folivo.trixnity.client.crypto.OlmSignService
import net.folivo.trixnity.client.crypto.VerifyResult
import net.folivo.trixnity.client.crypto.getCrossSigningKey
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.client.store.*
import net.folivo.trixnity.client.verification.KeyVerificationState
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.*
import net.folivo.trixnity.core.model.crypto.CrossSigningKeysUsage.*
import net.folivo.trixnity.core.model.crypto.Key.Ed25519Key
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import org.kodein.log.LoggerFactory
import kotlin.test.assertNotNull

@OptIn(ExperimentalKotest::class)
class KeyServiceTest : ShouldSpec({
    timeout = 30_000

    val alice = UserId("alice", "server")
    val bob = UserId("bob", "server")
    val bobDevice = "BOBDEVICE"
    lateinit var scope: CoroutineScope
    lateinit var store: Store
    val olmSignService = mockk<OlmSignService>()
    val api = mockk<MatrixApiClient>()

    lateinit var cut: KeyService

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        store = InMemoryStore(scope).apply { init() }
        cut = KeyService(store, olmSignService, api, LoggerFactory.default)
        coEvery { olmSignService.verifySelfSignedDeviceKeys(any()) } returns VerifyResult.Valid
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
            should("not add add changed devices to outdated keys") {
                store.keys.outdatedKeys.value = setOf(alice)
                cut.handleDeviceLists(SyncResponse.DeviceLists(changed = setOf(bob)))
                store.keys.outdatedKeys.value shouldContainExactly setOf(alice)
            }
        }
    }

    context(KeyService::handleOutdatedKeys.name) {
        val cedric = UserId("cedric", "server")
        val cedricDevice = "CEDRIC_DEVICE"
        val cedricKey1 = Signed<DeviceKeys, UserId>(DeviceKeys(cedric, cedricDevice, setOf(), keysOf()), mapOf())
        val aliceDevice2 = "ALICE_DEVICE_2"
        val aliceKey2 = Signed<DeviceKeys, UserId>(DeviceKeys(alice, aliceDevice2, setOf(), keysOf()), mapOf())
        beforeTest {
            coEvery { olmSignService.verify<DeviceKeys>(any()) } returns VerifyResult.Valid
            coEvery { olmSignService.verify<CrossSigningKeys>(any()) } returns VerifyResult.Valid
        }
        should("do nothing when no keys outdated") {
            cut.handleOutdatedKeys(setOf())
            verify { api wasNot Called }
        }
        context("master keys") {
            should("ignore when signatures are invalid") {
                val invalidKey = Signed(
                    CrossSigningKeys(alice, setOf(MasterKey), keysOf()),
                    mapOf(alice to keysOf(Ed25519Key("invalid", "invalid")))
                )
                coEvery { olmSignService.verify(invalidKey) } returns VerifyResult.Invalid("")
                coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns QueryKeysResponse(
                    mapOf(), mapOf(),
                    mapOf(alice to invalidKey),
                    mapOf(), mapOf()
                )
                cut.handleOutdatedKeys(setOf(alice))
                store.keys.getCrossSigningKeys(alice).shouldBeNull()
            }
            should("add master key") {
                val key = Signed<CrossSigningKeys, UserId>(
                    CrossSigningKeys(alice, setOf(MasterKey), keysOf()), mapOf()
                )
                coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns QueryKeysResponse(
                    mapOf(), mapOf(),
                    mapOf(alice to key),
                    mapOf(), mapOf()
                )
                cut.handleOutdatedKeys(setOf(alice))
                store.keys.getCrossSigningKeys(alice) shouldContainExactly setOf(
                    StoredCrossSigningKey(key, Valid(false))
                )
            }
            should("replace old master key and set trust level to ${MasterKeyChangedRecently::class.simpleName}") {
                val key = Signed<CrossSigningKeys, UserId>(
                    CrossSigningKeys(alice, setOf(MasterKey), keysOf()), mapOf()
                )
                store.keys.updateCrossSigningKeys(alice) {
                    setOf(
                        StoredCrossSigningKey(
                            Signed(key.signed, mapOf(alice to keysOf())),
                            Valid(false)
                        )
                    )
                }
                coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns QueryKeysResponse(
                    mapOf(), mapOf(),
                    mapOf(alice to key),
                    mapOf(), mapOf()
                )
                cut.handleOutdatedKeys(setOf(alice))
                store.keys.getCrossSigningKeys(alice) shouldContainExactly setOf(
                    StoredCrossSigningKey(key, MasterKeyChangedRecently(false))
                )

                store.keys.updateCrossSigningKeys(alice) {
                    setOf(
                        StoredCrossSigningKey(
                            Signed(key.signed, mapOf(alice to keysOf())),
                            MasterKeyChangedRecently(false)
                        )
                    )
                }
                cut.handleOutdatedKeys(setOf(alice))
                store.keys.getCrossSigningKeys(alice) shouldContainExactly setOf(
                    StoredCrossSigningKey(key, MasterKeyChangedRecently(false))
                )

                store.keys.updateCrossSigningKeys(alice) {
                    setOf(
                        StoredCrossSigningKey(
                            Signed(key.signed, mapOf(alice to keysOf())),
                            Valid(true)
                        )
                    )
                }
                cut.handleOutdatedKeys(setOf(alice))
                store.keys.getCrossSigningKeys(alice) shouldContainExactly setOf(
                    StoredCrossSigningKey(key, MasterKeyChangedRecently(true))
                )
            }
        }
        context("self signing keys") {
            should("ignore when signatures are invalid") {
                val invalidKey = Signed(
                    CrossSigningKeys(alice, setOf(SelfSigningKey), keysOf()),
                    mapOf(alice to keysOf(Ed25519Key("invalid", "invalid")))
                )
                coEvery { olmSignService.verify(invalidKey) } returns VerifyResult.Invalid("")
                coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns QueryKeysResponse(
                    mapOf(), mapOf(), mapOf(),
                    mapOf(alice to invalidKey),
                    mapOf()
                )
                cut.handleOutdatedKeys(setOf(alice))
                store.keys.getCrossSigningKeys(alice).shouldBeNull()
            }
            should("add self signing key") {
                val key = Signed<CrossSigningKeys, UserId>(
                    CrossSigningKeys(alice, setOf(SelfSigningKey), keysOf()), mapOf()
                )
                coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns QueryKeysResponse(
                    mapOf(), mapOf(), mapOf(),
                    mapOf(alice to key),
                    mapOf()
                )
                cut.handleOutdatedKeys(setOf(alice))
                store.keys.getCrossSigningKeys(alice) shouldContainExactly setOf(
                    StoredCrossSigningKey(key, Valid(false))
                )
            }
            should("replace self signing key") {
                val key = Signed<CrossSigningKeys, UserId>(
                    CrossSigningKeys(alice, setOf(SelfSigningKey), keysOf()), mapOf()
                )
                store.keys.updateCrossSigningKeys(alice) {
                    setOf(
                        StoredCrossSigningKey(
                            Signed(key.signed, mapOf(alice to keysOf())),
                            Valid(true)
                        )
                    )
                }
                coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns QueryKeysResponse(
                    mapOf(), mapOf(), mapOf(),
                    mapOf(alice to key),
                    mapOf()
                )
                cut.handleOutdatedKeys(setOf(alice))
                store.keys.getCrossSigningKeys(alice) shouldContainExactly setOf(
                    StoredCrossSigningKey(key, Valid(false))
                )
            }
        }
        context("user signing keys") {
            should("ignore when signatures are invalid") {
                val invalidKey = Signed(
                    CrossSigningKeys(alice, setOf(UserSigningKey), keysOf()),
                    mapOf(alice to keysOf(Ed25519Key("invalid", "invalid")))
                )
                coEvery { olmSignService.verify(invalidKey) } returns VerifyResult.Invalid("")
                coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns QueryKeysResponse(
                    mapOf(), mapOf(), mapOf(), mapOf(),
                    mapOf(alice to invalidKey)
                )
                cut.handleOutdatedKeys(setOf(alice))
                store.keys.getCrossSigningKeys(alice).shouldBeNull()
            }
            should("add user signing key") {
                val key = Signed<CrossSigningKeys, UserId>(
                    CrossSigningKeys(alice, setOf(UserSigningKey), keysOf()), mapOf()
                )
                coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns QueryKeysResponse(
                    mapOf(), mapOf(), mapOf(), mapOf(),
                    mapOf(alice to key),
                )
                cut.handleOutdatedKeys(setOf(alice))
                store.keys.getCrossSigningKeys(alice) shouldContainExactly setOf(
                    StoredCrossSigningKey(key, Valid(false))
                )
            }
            should("replace user signing key") {
                val key = Signed<CrossSigningKeys, UserId>(
                    CrossSigningKeys(alice, setOf(UserSigningKey), keysOf()), mapOf()
                )
                store.keys.updateCrossSigningKeys(alice) {
                    setOf(
                        StoredCrossSigningKey(
                            Signed(key.signed, mapOf(alice to keysOf())),
                            Valid(true)
                        )
                    )
                }
                coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns QueryKeysResponse(
                    mapOf(), mapOf(), mapOf(), mapOf(),
                    mapOf(alice to key),
                )
                cut.handleOutdatedKeys(setOf(alice))
                store.keys.getCrossSigningKeys(alice) shouldContainExactly setOf(
                    StoredCrossSigningKey(key, Valid(false))
                )
            }
        }
        context("device keys") {
            should("update outdated device keys") {
                coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns QueryKeysResponse(
                    mapOf(),
                    mapOf(cedric to mapOf(cedricDevice to cedricKey1), alice to mapOf(aliceDevice2 to aliceKey2)),
                    mapOf(), mapOf(), mapOf()
                ) andThen QueryKeysResponse(
                    mapOf(),
                    mapOf(cedric to mapOf(cedricDevice to cedricKey1), alice to mapOf()),
                    mapOf(), mapOf(), mapOf()
                )
                cut.handleOutdatedKeys(setOf(cedric, alice))
                val storedCedricKeys = store.keys.getDeviceKeys(cedric)
                assertNotNull(storedCedricKeys)
                storedCedricKeys shouldContainExactly mapOf(
                    cedricDevice to StoredDeviceKeys(
                        cedricKey1,
                        Valid(false)
                    )
                )
                val storedAliceKeys = store.keys.getDeviceKeys(alice)
                assertNotNull(storedAliceKeys)
                storedAliceKeys shouldContainExactly mapOf(
                    aliceDevice2 to StoredDeviceKeys(
                        aliceKey2,
                        Valid(false)
                    )
                )
                // check delete of device keys
                cut.handleOutdatedKeys(setOf(cedric, alice))
                val storedAliceKeysAfterDelete = store.keys.getDeviceKeys(alice)
                assertNotNull(storedAliceKeysAfterDelete)
                storedAliceKeysAfterDelete shouldContainExactly mapOf()
            }
            should("look for encrypted room, where the user participates and notify megolm sessions about new device keys") {
                coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns QueryKeysResponse(
                    mapOf(),
                    mapOf(cedric to mapOf(cedricDevice to cedricKey1), alice to mapOf(aliceDevice2 to aliceKey2)),
                    mapOf(), mapOf(), mapOf()
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
                store.roomState.updateAll(
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
                    )
                )

                store.olm.updateOutboundMegolmSession(room1) { StoredOutboundMegolmSession(room1, pickle = "") }
                store.olm.updateOutboundMegolmSession(room3) {
                    StoredOutboundMegolmSession(room3, newDevices = mapOf(cedric to setOf(cedricDevice)), pickle = "")
                }

                cut.handleOutdatedKeys(setOf(cedric, alice))

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
                            Valid(false),
                            CrossSigned(false),
                            NotAllDeviceKeysCrossSigned(false)
                        ) { levelBefore ->
                            coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns QueryKeysResponse(
                                mapOf(),
                                mapOf(alice to mapOf(aliceDevice2 to aliceKey2)),
                                mapOf(), mapOf(), mapOf()
                            )
                            store.keys.updateCrossSigningKeys(alice) {
                                setOf(
                                    StoredCrossSigningKey(
                                        Signed(
                                            CrossSigningKeys(alice, setOf(MasterKey), keysOf()),
                                            mapOf()
                                        ), levelBefore
                                    )
                                )
                            }
                            cut.handleOutdatedKeys(setOf(alice))
                            store.keys.getCrossSigningKey(alice, MasterKey)
                                ?.trustLevel shouldBe NotAllDeviceKeysCrossSigned(false)
                        }
                    }
                    context("keep trust level") {
                        withData(
                            MasterKeyChangedRecently(false),
                            Blocked
                        ) { levelBefore ->
                            coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns QueryKeysResponse(
                                mapOf(),
                                mapOf(alice to mapOf(aliceDevice2 to aliceKey2)),
                                mapOf(), mapOf(), mapOf()
                            )
                            store.keys.updateCrossSigningKeys(alice) {
                                setOf(
                                    StoredCrossSigningKey(
                                        Signed(
                                            CrossSigningKeys(alice, setOf(MasterKey), keysOf()),
                                            mapOf()
                                        ), levelBefore
                                    )
                                )
                            }
                            cut.handleOutdatedKeys(setOf(alice))
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
                        coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns QueryKeysResponse(
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
                    }
                    should("calculate trust level of master key when his trust level is ${NotAllDeviceKeysCrossSigned::class.simpleName}") {
                        store.keys.updateCrossSigningKeys(alice) {
                            setOf(StoredCrossSigningKey(aliceMasterKey, NotAllDeviceKeysCrossSigned(false)))
                        }
                        cut.handleOutdatedKeys(setOf(alice))
                        store.keys.getCrossSigningKey(alice, MasterKey)
                            ?.trustLevel shouldBe Valid(false)
                    }
                    should("do nothing when his trust level is not ${NotAllDeviceKeysCrossSigned::class.simpleName}") {
                        store.keys.updateCrossSigningKeys(alice) {
                            setOf(StoredCrossSigningKey(aliceMasterKey, CrossSigned(true)))
                        }
                        cut.handleOutdatedKeys(setOf(alice))
                        store.keys.getCrossSigningKey(alice, MasterKey)
                            ?.trustLevel shouldBe CrossSigned(true)
                    }
                }
            }
            context("manipulation of ") {
                should("signature") {
                    coEvery { olmSignService.verifySelfSignedDeviceKeys(cedricKey1) } returns VerifyResult.Invalid("")
                    coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns QueryKeysResponse(
                        mapOf(), mapOf(cedric to mapOf(cedricDevice to cedricKey1)), mapOf(), mapOf(), mapOf()
                    )
                    cut.handleOutdatedKeys(setOf(cedric))
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
                    coEvery { api.keys.getKeys(any(), any(), any(), any()) } returns QueryKeysResponse(
                        mapOf(), deviceKeys, mapOf(), mapOf(), mapOf()
                    )
                    cut.handleOutdatedKeys(setOf(alice, cedric))
                    val storedKeys = store.keys.getDeviceKeys(alice)
                    assertNotNull(storedKeys)
                    storedKeys shouldHaveSize 0
                }
            }
        }
    }

    context("calculateTrustLevel") {
        context("without key chain") {
            val deviceKeys = Signed<DeviceKeys, UserId>(
                DeviceKeys(
                    alice, "AAAAAA", setOf(EncryptionAlgorithm.Megolm, EncryptionAlgorithm.Olm),
                    keysOf(
                        Ed25519Key("AAAAAA", "edKeyValue"),
                        Key.Curve25519Key("AAAAAA", "cuKeyValue")
                    )
                ),
                mapOf()
            )
            should("be ${Valid::class.simpleName} + verified, when key is verified") {
                store.keys.saveKeyVerificationState(
                    Ed25519Key("AAAAAA", "edKeyValue"), alice, "AAAAAA",
                    KeyVerificationState.Verified("edKeyValue")
                )
                cut.calculateDeviceKeysTrustLevel(deviceKeys) shouldBe Valid(true)
            }
            should("be ${Blocked::class.simpleName}, when key is blocked") {
                store.keys.saveKeyVerificationState(
                    Ed25519Key("AAAAAA", "edKeyValue"), alice, "AAAAAA",
                    KeyVerificationState.Blocked("edKeyValue")
                )
                cut.calculateDeviceKeysTrustLevel(deviceKeys) shouldBe Blocked
            }
            should("be ${Valid::class.simpleName}, when there is no master key") {
                cut.calculateDeviceKeysTrustLevel(deviceKeys) shouldBe Valid(false)
            }
            should("be ${NotCrossSigned::class.simpleName}, when there is a master key") {
                store.keys.updateCrossSigningKeys(alice) {
                    setOf(
                        StoredCrossSigningKey(
                            Signed(CrossSigningKeys(alice, setOf(MasterKey), keysOf()), mapOf()),
                            Valid(false)
                        )
                    )
                }
                cut.calculateDeviceKeysTrustLevel(deviceKeys) shouldBe NotCrossSigned
            }
        }
        context("with key chain: BOB_DEVICE <- BOB_SSK <- BOB_MSK <- ALICE_USK <- ALICE_MSK <- ALICE_DEVICE") {
            val deviceKeys = Signed(
                DeviceKeys(
                    bob, "BOB_DEVICE", setOf(EncryptionAlgorithm.Megolm, EncryptionAlgorithm.Olm),
                    keysOf(
                        Ed25519Key("BOB_DEVICE", "edKeyValue"),
                        Key.Curve25519Key("BOB_DEVICE", "cuKeyValue")
                    )
                ),
                mapOf(
                    bob to keysOf(Ed25519Key("BOB_SSK", "..."))
                )
            )
            beforeTest {
                store.keys.updateCrossSigningKeys(bob) {
                    setOf(
                        StoredCrossSigningKey(
                            Signed(
                                CrossSigningKeys(
                                    bob, setOf(SelfSigningKey), keysOf(Ed25519Key("BOB_SSK", "..."))
                                ),
                                mapOf(bob to keysOf(Ed25519Key("BOB_MSK", "...")))
                            ), Valid(false)
                        ),
                        StoredCrossSigningKey(
                            Signed(
                                CrossSigningKeys(
                                    bob, setOf(MasterKey), keysOf(Ed25519Key("BOB_MSK", "..."))
                                ),
                                mapOf(alice to keysOf(Ed25519Key("ALICE_USK", "...")))
                            ), Valid(false)
                        )
                    )
                }
                store.keys.updateCrossSigningKeys(alice) {
                    setOf(
                        StoredCrossSigningKey(
                            Signed(
                                CrossSigningKeys(
                                    alice, setOf(UserSigningKey), keysOf(Ed25519Key("ALICE_USK", "..."))
                                ),
                                mapOf(alice to keysOf(Ed25519Key("ALICE_MSK", "...")))
                            ), Valid(false)
                        ),
                        StoredCrossSigningKey(
                            Signed(
                                CrossSigningKeys(
                                    alice, setOf(MasterKey), keysOf(Ed25519Key("ALICE_MSK", "..."))
                                ),
                                mapOf(alice to keysOf(Ed25519Key("ALICE_DEVICE", "...")))
                            ), Valid(true)
                        )
                    )
                }
                store.keys.updateDeviceKeys(alice) {
                    mapOf(
                        "ALICE_DEVICE" to StoredDeviceKeys(
                            Signed(
                                DeviceKeys(
                                    alice, "ALICE_DEVICE", setOf(EncryptionAlgorithm.Megolm, EncryptionAlgorithm.Olm),
                                    keysOf(
                                        Ed25519Key("ALICE_DEVICE", "..."),
                                        Key.Curve25519Key("ALICE_DEVICE", "...")
                                    )
                                ),
                                mapOf()
                            ), Valid(true)
                        )
                    )
                }
            }
            should("be ${CrossSigned::class.simpleName} + verified, when there is a verified key in key chain") {
                store.keys.saveKeyVerificationState(
                    Ed25519Key("ALICE_DEVICE", "..."), alice, "ALICE_DEVICE",
                    KeyVerificationState.Verified("...")
                )
                cut.calculateDeviceKeysTrustLevel(deviceKeys) shouldBe CrossSigned(true)
            }
            should("be ${Blocked::class.simpleName}, when there is a blocked key in key chain") {
                store.keys.saveKeyVerificationState(
                    Ed25519Key("BOB_DEVICE", "..."), bob, "BOB_DEVICE",
                    KeyVerificationState.Blocked("...")
                )
                cut.calculateDeviceKeysTrustLevel(deviceKeys) shouldBe Blocked
            }
        }
    }
})