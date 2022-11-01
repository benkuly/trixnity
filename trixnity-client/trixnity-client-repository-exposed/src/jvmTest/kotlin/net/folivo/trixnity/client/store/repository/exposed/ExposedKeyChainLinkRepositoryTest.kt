package net.folivo.trixnity.client.store.repository.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.KeyChainLink
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedKeyChainLinkRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var cut: ExposedKeyChainLinkRepository

    beforeTest {
        createDatabase()
        newSuspendedTransaction {
            SchemaUtils.create(ExposedKeyChainLink)
        }
        cut = ExposedKeyChainLinkRepository()
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

        newSuspendedTransaction {
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