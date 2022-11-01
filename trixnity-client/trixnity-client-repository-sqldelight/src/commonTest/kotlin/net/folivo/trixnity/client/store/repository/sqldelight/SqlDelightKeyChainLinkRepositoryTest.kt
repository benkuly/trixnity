package net.folivo.trixnity.client.store.repository.sqldelight

import com.squareup.sqldelight.db.SqlDriver
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import net.folivo.trixnity.client.store.KeyChainLink
import net.folivo.trixnity.client.store.sqldelight.db.Database
import net.folivo.trixnity.client.store.repository.sqldelight.testutils.createDriverWithSchema
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key

class SqlDelightKeyChainLinkRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var cut: SqlDelightKeyChainLinkRepository
    lateinit var driver: SqlDriver

    beforeTest {
        driver = createDriverWithSchema()
        cut =
            SqlDelightKeyChainLinkRepository(Database(driver).keysQueries, Dispatchers.Default)
    }
    afterTest {
        driver.close()
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
})