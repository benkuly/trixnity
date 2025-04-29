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