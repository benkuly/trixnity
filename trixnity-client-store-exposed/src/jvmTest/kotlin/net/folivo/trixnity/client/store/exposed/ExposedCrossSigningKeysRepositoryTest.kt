package net.folivo.trixnity.client.store.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.crypto.KeySignatureTrustLevel
import net.folivo.trixnity.client.store.StoredCrossSigningKey
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.crypto.*
import net.folivo.trixnity.core.serialization.createMatrixJson
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedCrossSigningKeysRepositoryTest : ShouldSpec({
    lateinit var cut: ExposedCrossSigningKeysRepository

    beforeTest {
        createDatabase()
        newSuspendedTransaction {
            SchemaUtils.create(ExposedCrossSigningKeys)
        }
        cut = ExposedCrossSigningKeysRepository(createMatrixJson())
    }
    should("save, get and delete") {
        val alice = UserId("alice", "server")
        val bob = UserId("bob", "server")
        val aliceCrossSigningKeys = setOf(
            StoredCrossSigningKey(
                Signed(
                    CrossSigningKeys(
                        alice,
                        setOf(CrossSigningKeysUsage.MasterKey),
                        keysOf(Key.Ed25519Key(null, "aliceEdKey1"))
                    ), mapOf(bob to keysOf(Key.Ed25519Key("BOBDE", "keyValue")))
                ), KeySignatureTrustLevel.Valid(true)
            ),
            StoredCrossSigningKey(
                Signed(
                    CrossSigningKeys(
                        alice,
                        setOf(CrossSigningKeysUsage.MasterKey),
                        keysOf(Key.Ed25519Key(null, "aliceEdKey2"))
                    ), mapOf(alice to keysOf(Key.Ed25519Key("ALICE", "keyValue")))
                ), KeySignatureTrustLevel.Valid(true)
            ),
        )
        val bobCrossSigningKeys = setOf(
            StoredCrossSigningKey(
                Signed(
                    CrossSigningKeys(
                        bob,
                        setOf(CrossSigningKeysUsage.MasterKey),
                        keysOf(Key.Ed25519Key(null, "bobEdKey1"))
                    ), mapOf(bob to keysOf(Key.Ed25519Key("BOBDE", "keyValue")))
                ), KeySignatureTrustLevel.Valid(true)
            ),
        )
        val bobDeviceKeysCopy = setOf(
            StoredCrossSigningKey(
                Signed(
                    CrossSigningKeys(
                        bob,
                        setOf(CrossSigningKeysUsage.MasterKey),
                        keysOf(Key.Ed25519Key(null, "bobEdKey2"))
                    ), mapOf(bob to keysOf(Key.Ed25519Key("BOBDE", "keyValue2")))
                ), KeySignatureTrustLevel.Valid(true)
            ),
        )
        newSuspendedTransaction {
            cut.save(alice, aliceCrossSigningKeys)
            cut.save(bob, bobCrossSigningKeys)
            cut.get(alice) shouldBe aliceCrossSigningKeys
            cut.get(bob) shouldBe bobCrossSigningKeys
            cut.save(bob, bobDeviceKeysCopy)
            cut.get(bob) shouldBe bobDeviceKeysCopy
            cut.delete(alice)
            cut.get(alice) shouldBe null
        }
    }
})