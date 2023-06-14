package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.store.KeyChainLink
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomKeyChainLinkRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomKeyChainLinkRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomKeyChainLinkRepository(db)
    }

    @Test
    fun `Save, get and delete`() = runTest {
        val link1 = KeyChainLink(
            signingUserId = UserId("bob", "server"),
            signingKey = Key.Ed25519Key("BOB_DEVICE", "keyValueB"),
            signedUserId = UserId("alice", "server"),
            signedKey = Key.Ed25519Key("ALICE_DEVICE", "keyValueA")
        )
        val link2 = KeyChainLink(
            signingUserId = UserId("cedric", "server"),
            signingKey = Key.Ed25519Key("CEDRIC_DEVICE", "keyValueC"),
            signedUserId = UserId("alice", "server"),
            signedKey = Key.Ed25519Key("ALICE_DEVICE", "keyValueA")
        )
        val link3 = KeyChainLink(
            signingUserId = UserId("bob", "server"),
            signingKey = Key.Ed25519Key("BOB_DEVICE", "keyValueB"),
            signedUserId = UserId("cedric", "server"),
            signedKey = Key.Ed25519Key("CEDRIC_DEVICE", "keyValueC")
        )

        repo.save(link1)
        repo.save(link2)
        repo.save(link3)
        repo.getBySigningKey(
            UserId("bob", "server"),
            Key.Ed25519Key("BOB_DEVICE", "keyValueB")
        ) shouldBe setOf(link1, link3)
        repo.deleteBySignedKey(
            UserId("alice", "server"),
            Key.Ed25519Key("ALICE_DEVICE", "keyValueA")
        )
        repo.getBySigningKey(
            UserId("bob", "server"),
            Key.Ed25519Key("BOB_DEVICE", "keyValueB")
        ) shouldBe setOf(link3)
    }
}
