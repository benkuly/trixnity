package net.folivo.trixnity.client.store.repository.realm

import com.benasher44.uuid.uuid4
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import java.io.File

class RealmOutdatedKeysRepositoryTest : ShouldSpec({
    timeout = 10_000
    val realmDbPath = "build/${uuid4()}"
    lateinit var realm: Realm
    lateinit var cut: RealmOutdatedKeysRepository

    beforeTest {
        File(realmDbPath).deleteRecursively()
        realm = Realm.open(
            RealmConfiguration.Builder(
                schema = setOf(
                    RealmOutdatedKeys::class,
                )
            ).apply { directory(realmDbPath) }.build()
        )

        cut = RealmOutdatedKeysRepository(createMatrixEventJson())
    }
    afterTest {
        File(realmDbPath).deleteRecursively()
    }
    should("save, get and delete") {
        val alice = UserId("alice", "server")
        val bob = UserId("bob", "server")

        writeTransaction(realm) {
            cut.save(1, setOf(alice))
            cut.get(1) shouldContainExactly setOf(alice)
            cut.save(1, setOf(alice, bob))
            cut.get(1) shouldContainExactly setOf(alice, bob)
            cut.delete(1)
            cut.get(1) shouldBe null
        }
    }
})