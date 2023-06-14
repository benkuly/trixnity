package net.folivo.trixnity.client.store.repository.indexeddb

import com.benasher44.uuid.uuid4
import com.juul.indexeddb.openDatabase
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.KeyChainLink
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key
import net.folivo.trixnity.core.serialization.createMatrixEventJson

class IndexedDBKeyChainLinkRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var cut: IndexedDBKeyChainLinkRepository
    lateinit var rtm: IndexedDBRepositoryTransactionManager

    beforeTest {
        cut = IndexedDBKeyChainLinkRepository(createMatrixEventJson())
        val db = openDatabase(uuid4().toString(), 1) { database, oldVersion, _ ->
            IndexedDBKeyChainLinkRepository.apply { migrate(database, oldVersion) }
        }
        rtm = IndexedDBRepositoryTransactionManager(db, arrayOf(cut.objectStoreName))
    }
    should("save, get and delete") {
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

        rtm.writeTransaction {
            cut.save(link1)
            cut.save(link2)
            cut.save(link3)
            cut.getBySigningKey(
                UserId("bob", "server"),
                Key.Ed25519Key("BOB_DEVICE", "keyValueB")
            ) shouldBe setOf(link1, link3)
            cut.deleteBySignedKey(
                UserId("alice", "server"),
                Key.Ed25519Key("ALICE_DEVICE", "keyValueA")
            )
            cut.getBySigningKey(
                UserId("bob", "server"),
                Key.Ed25519Key("BOB_DEVICE", "keyValueB")
            ) shouldBe setOf(link3)
        }
    }
})