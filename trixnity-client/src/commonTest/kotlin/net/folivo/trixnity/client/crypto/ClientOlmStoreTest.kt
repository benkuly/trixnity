package net.folivo.trixnity.client.crypto

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.getInMemoryAccountStore
import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.getInMemoryOlmStore
import net.folivo.trixnity.client.getInMemoryRoomStateStore
import net.folivo.trixnity.client.store.KeySignatureTrustLevel
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.DeviceKeys
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.core.model.keys.SignedDeviceKeys
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import kotlin.test.Test

class ClientOlmStoreTest : TrixnityBaseTest() {

    private val alice = UserId("alice", "server")
    private val aliceDevice = "ALICEDEVICE"

    private val keyStore = getInMemoryKeyStore()

    private val cut = ClientOlmStore(
        accountStore = getInMemoryAccountStore { updateAccount { it?.copy(olmPickleKey = "") } },
        olmCryptoStore = getInMemoryOlmStore(),
        keyStore = keyStore,
        roomStateStore = getInMemoryRoomStateStore(),
        loadMembersService = { _, _ -> },
    )

    @Test
    fun `findCurve25519Key » identity key is present » return identity key`() = runTest {
        keyStore.updateDeviceKeys(alice) {
            mapOf(
                aliceDevice to StoredDeviceKeys(
                    SignedDeviceKeys(
                        DeviceKeys(alice, aliceDevice, setOf(), keysOf(Key.Curve25519Key(null, "key")))
                    ),
                    KeySignatureTrustLevel.Valid(true)
                )
            )
        }
        cut.findCurve25519Key(alice, aliceDevice) shouldBe Key.Curve25519Key(null, "key")
    }

    @Test
    fun `findCurve25519Key » identity key is not present » fetch and return identity key when found`() =
        runTest {
            val result = async { cut.findCurve25519Key(alice, aliceDevice) }
            keyStore.getOutdatedKeysFlow().first { it.contains(alice) }
            keyStore.updateDeviceKeys(alice) {
                mapOf(
                    aliceDevice to StoredDeviceKeys(
                        SignedDeviceKeys(
                            DeviceKeys(alice, aliceDevice, setOf(), keysOf(Key.Curve25519Key(null, "key")))
                        ),
                        KeySignatureTrustLevel.Valid(true)
                    )
                )
            }
            keyStore.updateOutdatedKeys { setOf() }
            result.await() shouldBe Key.Curve25519Key(null, "key")
        }

    @Test
    fun `findCurve25519Key » identity key is not present » return null when no identity key found`() =
        runTest {
            val result = async { cut.findCurve25519Key(alice, aliceDevice) }
            keyStore.getOutdatedKeysFlow().first { it.contains(alice) }
            keyStore.updateOutdatedKeys { setOf() }
            result.await() shouldBe null
        }

    @Test
    fun `findEd25519Key » signing key is present » return signing key`() = runTest {
        keyStore.updateDeviceKeys(alice) {
            mapOf(
                aliceDevice to StoredDeviceKeys(
                    SignedDeviceKeys(
                        DeviceKeys(alice, aliceDevice, setOf(), keysOf(Key.Ed25519Key(null, "key")))
                    ),
                    KeySignatureTrustLevel.Valid(true)
                )
            )
        }
        cut.findEd25519Key(alice, aliceDevice) shouldBe Key.Ed25519Key(null, "key")
    }

    @Test
    fun `findEd25519Key » signing key is not present » fetch and return signing key when found`() = runTest {
        val result = async { cut.findEd25519Key(alice, aliceDevice) }
        keyStore.getOutdatedKeysFlow().first { it.contains(alice) }
        keyStore.updateDeviceKeys(alice) {
            mapOf(
                aliceDevice to StoredDeviceKeys(
                    SignedDeviceKeys(
                        DeviceKeys(alice, aliceDevice, setOf(), keysOf(Key.Ed25519Key(null, "key")))
                    ),
                    KeySignatureTrustLevel.Valid(true)
                )
            )
        }
        keyStore.updateOutdatedKeys { setOf() }
        result.await() shouldBe Key.Ed25519Key(null, "key")
    }

    @Test
    fun `findEd25519Key » signing key is not present » return null when no signing key found`() = runTest {
        val result = async { cut.findEd25519Key(alice, aliceDevice) }
        keyStore.getOutdatedKeysFlow().first { it.contains(alice) }
        keyStore.updateOutdatedKeys { setOf() }
        result.await() shouldBe null
    }

    @Test
    fun `findDeviceKeys » device key is present » return device key`() = runTest {
        val deviceKeys = DeviceKeys(alice, aliceDevice, setOf(), keysOf(Key.Curve25519Key(null, "key")))
        keyStore.updateDeviceKeys(alice) {
            mapOf(
                aliceDevice to StoredDeviceKeys(
                    SignedDeviceKeys(deviceKeys),
                    KeySignatureTrustLevel.Valid(true)
                )
            )
        }
        cut.findDeviceKeys(alice, Curve25519KeyValue("key")) shouldBe deviceKeys
    }

    @Test
    fun `findDeviceKeys » device key is not present » fetch and return device key when found`() = runTest {
        val deviceKeys = DeviceKeys(alice, aliceDevice, setOf(), keysOf(Key.Curve25519Key(null, "key")))

        val result = async { cut.findDeviceKeys(alice, Curve25519KeyValue("key")) }
        keyStore.getOutdatedKeysFlow().first { it.contains(alice) }
        keyStore.updateDeviceKeys(alice) {
            mapOf(
                aliceDevice to StoredDeviceKeys(
                    SignedDeviceKeys(deviceKeys),
                    KeySignatureTrustLevel.Valid(true)
                )
            )
        }
        keyStore.updateOutdatedKeys { setOf() }
        result.await() shouldBe deviceKeys
    }

    @Test
    fun `findDeviceKeys » device key is not present » return null when no device key found`() = runTest {
        val result = async { cut.findDeviceKeys(alice, Curve25519KeyValue("key")) }
        keyStore.getOutdatedKeysFlow().first { it.contains(alice) }
        keyStore.updateOutdatedKeys { setOf() }
        result.await() shouldBe null
    }
}