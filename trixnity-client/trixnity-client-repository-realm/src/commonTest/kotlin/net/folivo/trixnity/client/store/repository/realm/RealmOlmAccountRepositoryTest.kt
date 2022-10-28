package net.folivo.trixnity.client.store.repository.realm

import com.benasher44.uuid.uuid4
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import java.io.File

class RealmOlmAccountRepositoryTest : ShouldSpec({
    timeout = 10_000
    val realmDbPath = "build/${uuid4()}"
    lateinit var realm: Realm
    lateinit var cut: RealmOlmAccountRepository

    beforeTest {
        File(realmDbPath).deleteRecursively()
        realm = Realm.open(
            RealmConfiguration.Builder(
                schema = setOf(
                    RealmOlmAccount::class,
                )
            ).apply { directory(realmDbPath) }.build()
        )

        cut = RealmOlmAccountRepository()
    }
    afterTest {
        File(realmDbPath).deleteRecursively()
    }
    should("save, get and delete") {
        writeTransaction(realm) {
            cut.save(1, "olm")
            cut.get(1) shouldBe "olm"
            cut.save(1, "newOlm")
            cut.get(1) shouldBe "newOlm"
            cut.delete(1)
            cut.get(1) shouldBe null
        }
    }
})