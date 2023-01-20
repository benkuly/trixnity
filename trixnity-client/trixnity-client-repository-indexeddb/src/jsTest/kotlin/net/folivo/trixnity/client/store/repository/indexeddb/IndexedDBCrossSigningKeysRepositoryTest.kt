package net.folivo.trixnity.client.store.repository.indexeddb

import com.benasher44.uuid.uuid4
import com.juul.indexeddb.openDatabase
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.KeySignatureTrustLevel
import net.folivo.trixnity.client.store.StoredCrossSigningKeys
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.*
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class IndexedDBCrossSigningKeysRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var cut: IndexedDBCrossSigningKeysRepository
    lateinit var rtm: IndexedDBRepositoryTransactionManager

    beforeTest {
        cut = IndexedDBCrossSigningKeysRepository(createMatrixEventJson())
        val db = openDatabase(uuid4().toString(), 1) { database, oldVersion, _ ->
            IndexedDBCrossSigningKeysRepository.apply { migrate(database, oldVersion) }
        }
        rtm = IndexedDBRepositoryTransactionManager(db, arrayOf(cut.objectStoreName))
    }
    should("save, get and delete") {
        val alice = UserId("alice", "server")
        val bob = UserId("bob", "server")
        val aliceCrossSigningKeys = setOf(
            StoredCrossSigningKeys(
                Signed(
                    CrossSigningKeys(
                        alice,
                        setOf(CrossSigningKeysUsage.MasterKey),
                        keysOf(Key.Ed25519Key(null, "aliceEdKey1"))
                    ), mapOf(bob to keysOf(Key.Ed25519Key("BOBDE", "keyValue")))
                ), KeySignatureTrustLevel.Valid(true)
            ),
            StoredCrossSigningKeys(
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
            StoredCrossSigningKeys(
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
            StoredCrossSigningKeys(
                Signed(
                    CrossSigningKeys(
                        bob,
                        setOf(CrossSigningKeysUsage.MasterKey),
                        keysOf(Key.Ed25519Key(null, "bobEdKey2"))
                    ), mapOf(bob to keysOf(Key.Ed25519Key("BOBDE", "keyValue2")))
                ), KeySignatureTrustLevel.Valid(true)
            ),
        )
        rtm.writeTransaction {
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