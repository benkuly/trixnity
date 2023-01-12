package net.folivo.trixnity.client.store.repository.realm

import com.benasher44.uuid.uuid4
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import kotlinx.datetime.Instant


class RealmOlmForgetFallbackKeyAfterRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var realm: Realm
    lateinit var cut: RealmOlmForgetFallbackKeyAfterRepository

    beforeTest {
        val realmDbPath = "build/test-db/${uuid4()}"
        realm = Realm.open(
            RealmConfiguration.Builder(
                schema = setOf(
                    RealmOlmForgetFallbackKeyAfter::class,
                )
            ).apply { directory(realmDbPath) }.build()
        )

        cut = RealmOlmForgetFallbackKeyAfterRepository()
    }
    should("save, get and delete") {
        writeTransaction(realm) {
            cut.save(1, Instant.fromEpochMilliseconds(24))
            cut.get(1) shouldBe Instant.fromEpochMilliseconds(24)
            cut.save(1, Instant.fromEpochMilliseconds(2424))
            cut.get(1) shouldBe Instant.fromEpochMilliseconds(2424)
            cut.delete(1)
            cut.get(1) shouldBe null
        }
    }
})