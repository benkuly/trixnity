package net.folivo.trixnity.client.crypto

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import net.folivo.trixnity.client.getInMemoryAccountStore
import net.folivo.trixnity.client.getInMemoryKeyStore
import net.folivo.trixnity.client.getInMemoryOlmStore
import net.folivo.trixnity.client.getInMemoryRoomStateStore
import net.folivo.trixnity.client.store.KeySignatureTrustLevel
import net.folivo.trixnity.client.store.KeyStore
import net.folivo.trixnity.client.store.StoredDeviceKeys
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.DeviceKeys
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.core.model.keys.SignedDeviceKeys
import net.folivo.trixnity.core.model.keys.keysOf

class ClientOlmStoreTest : ShouldSpec(body)

private val body: ShouldSpec.() -> Unit = {
    timeout = 15_000

    val alice = UserId("alice", "server")
    val aliceDevice = "ALICEDEVICE"
    lateinit var scope: CoroutineScope
    lateinit var keyStore: KeyStore
    lateinit var cut: ClientOlmStore

    beforeTest {
        scope = CoroutineScope(Dispatchers.Default)
        keyStore = getInMemoryKeyStore(scope)
        cut = ClientOlmStore(
            accountStore = getInMemoryAccountStore(scope).apply { updateAccount { it?.copy(olmPickleKey = "") } },
            olmCryptoStore = getInMemoryOlmStore(scope),
            keyStore = keyStore,
            roomStateStore = getInMemoryRoomStateStore(scope),
            loadMembersService = { _, _ -> },
        )
    }

    afterTest {
        scope.cancel()
    }

    context(ClientOlmStore::findCurve25519Key.name) {
        context("identity key is present") {
            should("return identity key") {
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
        }
        context("identity key is not present") {
            should("fetch and return identity key when found") {
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
            should("return null when no identity key found") {
                val result = async { cut.findCurve25519Key(alice, aliceDevice) }
                keyStore.getOutdatedKeysFlow().first { it.contains(alice) }
                keyStore.updateOutdatedKeys { setOf() }
                result.await() shouldBe null
            }
        }
    }
    context(ClientOlmStore::findEd25519Key.name) {
        context("signing key is present") {
            should("return signing key") {
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
        }
        context("signing key is not present") {
            should("fetch and return signing key when found") {
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
            should("return null when no signing key found") {
                val result = async { cut.findEd25519Key(alice, aliceDevice) }
                keyStore.getOutdatedKeysFlow().first { it.contains(alice) }
                keyStore.updateOutdatedKeys { setOf() }
                result.await() shouldBe null
            }
        }
    }
    context(ClientOlmStore::findDeviceKeys.name) {
        context("device key is present") {
            should("return device key") {
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
        }
        context("device key is not present") {
            should("fetch and return device key when found") {
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
            should("return null when no device key found") {
                val result = async { cut.findDeviceKeys(alice, Curve25519KeyValue("key")) }
                keyStore.getOutdatedKeysFlow().first { it.contains(alice) }
                keyStore.updateOutdatedKeys { setOf() }
                result.await() shouldBe null
            }
        }
    }
}