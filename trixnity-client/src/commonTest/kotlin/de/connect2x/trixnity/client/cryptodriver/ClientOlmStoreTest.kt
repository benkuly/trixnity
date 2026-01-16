package de.connect2x.trixnity.client.cryptodriver

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import de.connect2x.trixnity.client.getInMemoryAccountStore
import de.connect2x.trixnity.client.getInMemoryKeyStore
import de.connect2x.trixnity.client.getInMemoryOlmStore
import de.connect2x.trixnity.client.getInMemoryRoomStateStore
import de.connect2x.trixnity.client.store.KeySignatureTrustLevel
import de.connect2x.trixnity.client.store.StoredDeviceKeys
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.DeviceKeys
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.keys.SignedDeviceKeys
import de.connect2x.trixnity.core.model.keys.keysOf
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import kotlin.test.Test

class ClientOlmStoreTest : TrixnityBaseTest() {

    private val alice = UserId("alice", "server")
    private val aliceDevice = "ALICEDEVICE"

    private val keyStore = getInMemoryKeyStore()

    private val cut = ClientOlmStore(
        accountStore = getInMemoryAccountStore(),
        olmCryptoStore = getInMemoryOlmStore(),
        keyStore = keyStore,
        roomStateStore = getInMemoryRoomStateStore(),
        loadMembersService = { _, _ -> },
    )

    @Test
    fun `getDeviceKeys » identity key is present » return identity key`() = runTest {
        val deviceKeys = DeviceKeys(alice, aliceDevice, setOf(), keysOf(Key.Curve25519Key(null, "key")))

        keyStore.updateDeviceKeys(alice) {
            mapOf(
                aliceDevice to StoredDeviceKeys(
                    SignedDeviceKeys(deviceKeys),
                    KeySignatureTrustLevel.Valid(true)
                )
            )
        }
        cut.getDeviceKeys(alice)?.get(aliceDevice) shouldBe deviceKeys
    }

    @Test
    fun `getDeviceKeys » identity key is not present » fetch and return identity key when found`() =
        runTest {
            val deviceKeys = DeviceKeys(alice, aliceDevice, setOf(), keysOf(Key.Curve25519Key(null, "key")))

            val result = async { cut.getDeviceKeys(alice) }
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
            result.await()?.get(aliceDevice) shouldBe deviceKeys
        }

    @Test
    fun `getDeviceKeys » identity key is not present » return null when no identity key found`() =
        runTest {
            val result = async { cut.getDeviceKeys(alice) }
            keyStore.getOutdatedKeysFlow().first { it.contains(alice) }
            keyStore.updateOutdatedKeys { setOf() }
            result.await() shouldBe null
        }
}