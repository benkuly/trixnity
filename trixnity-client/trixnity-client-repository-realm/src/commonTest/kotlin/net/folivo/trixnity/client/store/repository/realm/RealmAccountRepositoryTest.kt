package net.folivo.trixnity.client.store.repository.realm

import com.benasher44.uuid.uuid4
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import net.folivo.trixnity.client.store.Account
import net.folivo.trixnity.core.model.UserId
import java.io.File

class RealmAccountRepositoryTest : ShouldSpec({
    timeout = 10_000
    val realmDbPath = "build/${uuid4()}"
    lateinit var realm: Realm
    lateinit var cut: RealmAccountRepository

    beforeTest {
        File(realmDbPath).deleteRecursively()
        realm = Realm.open(
            RealmConfiguration.Builder(
                schema = setOf(
                    RealmAccount::class,
                )
            ).apply { directory(realmDbPath) }.build()
        )

        cut = RealmAccountRepository()
    }
    afterTest {
        File(realmDbPath).deleteRecursively()
    }
    should("save, get and delete") {
        val account = Account(
            "",
            "http://host",
            UserId("alice", "server"),
            "aliceDevice",
            "accessToken",
            "syncToken",
            "filterId",
            "backgroundFilterId",
            "displayName",
            "mxc://localhost/123456",
        )
        writeTransaction(realm) {
            cut.save(1, account)
            cut.get(1) shouldBe account
            val accountCopy = account.copy(syncBatchToken = "otherSyncToken")
            cut.save(1, accountCopy)
            cut.get(1) shouldBe accountCopy
            cut.delete(1)
            cut.get(1) shouldBe null
        }
    }
})