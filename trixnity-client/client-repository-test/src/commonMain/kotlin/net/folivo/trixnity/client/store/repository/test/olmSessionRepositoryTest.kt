package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant.Companion.fromEpochMilliseconds
import net.folivo.trixnity.client.store.repository.OlmSessionRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import net.folivo.trixnity.crypto.olm.StoredOlmSession
import org.koin.core.Koin


fun ShouldSpec.olmSessionRepositoryTest(diReceiver: () -> Koin) {
    lateinit var cut: OlmSessionRepository
    lateinit var rtm: RepositoryTransactionManager
    beforeTest {
        val di = diReceiver()
        cut = di.get()
        rtm = di.get()
    }
    should("olmSessionRepositoryTest: save, get and delete") {
        val key1 = Curve25519KeyValue("curve1")
        val key2 = Curve25519KeyValue("curve2")
        val session1 =
            StoredOlmSession(key1, "session1", fromEpochMilliseconds(1234), fromEpochMilliseconds(1234), pickled = "1")
        val session2 =
            StoredOlmSession(key2, "session2", fromEpochMilliseconds(1234), fromEpochMilliseconds(1234), pickled = "2")
        val session3 =
            StoredOlmSession(key2, "session3", fromEpochMilliseconds(1234), fromEpochMilliseconds(1234), pickled = "2")

        rtm.writeTransaction {
            cut.save(key1, setOf(session1))
            cut.save(key2, setOf(session2))
            cut.get(key1) shouldContainExactly setOf(session1)
            cut.get(key2) shouldContainExactly setOf(session2)
            cut.save(key2, setOf(session2, session3))
            cut.get(key2) shouldContainExactly setOf(session2, session3)
            cut.delete(key1)
            cut.get(key1) shouldBe null
        }
    }
}