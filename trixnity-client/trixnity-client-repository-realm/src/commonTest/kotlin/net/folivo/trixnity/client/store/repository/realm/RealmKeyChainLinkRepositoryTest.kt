package net.folivo.trixnity.client.store.repository.realm

import com.benasher44.uuid.uuid4
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import net.folivo.trixnity.client.store.KeyChainLink
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.keys.Key
import java.io.File

class RealmKeyChainLinkRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var realm: Realm
    lateinit var cut: RealmKeyChainLinkRepository

    beforeTest {
        val realmDbPath = "build/test-db/${uuid4()}"
        realm = Realm.open(
            RealmConfiguration.Builder(
                schema = setOf(
                    RealmKeyChainLink::class,
                )
            ).apply { directory(realmDbPath) }.build()
        )

        cut = RealmKeyChainLinkRepository()
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

        writeTransaction(realm) {
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