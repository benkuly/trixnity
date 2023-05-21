package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.store.KeySignatureTrustLevel
import net.folivo.trixnity.client.store.StoredCrossSigningKeys
import net.folivo.trixnity.client.store.repository.test.buildTestDatabase
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.CrossSigningKeys
import net.folivo.trixnity.core.model.keys.CrossSigningKeysUsage
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.model.keys.Signed
import net.folivo.trixnity.core.model.keys.keysOf
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomCrossSigningKeysRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomCrossSigningKeysRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomCrossSigningKeysRepository(db, createMatrixEventJson())
    }

    @Test
    fun `Save, get and delete`() = runTest {
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

        repo.save(alice, aliceCrossSigningKeys)
        repo.save(bob, bobCrossSigningKeys)
        repo.get(alice) shouldBe aliceCrossSigningKeys
        repo.get(bob) shouldBe bobCrossSigningKeys
        repo.save(bob, bobDeviceKeysCopy)
        repo.get(bob) shouldBe bobDeviceKeysCopy
        repo.delete(alice)
        repo.get(alice) shouldBe null
    }
}
