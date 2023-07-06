package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.store.repository.OlmForgetFallbackKeyAfterRepository
import net.folivo.trixnity.client.store.transaction.RepositoryTransactionManager
import org.koin.core.Koin


fun ShouldSpec.olmForgetFallbackKeyAfterRepositoryTest(diReceiver: () -> Koin) {
    lateinit var cut: OlmForgetFallbackKeyAfterRepository
    lateinit var rtm: RepositoryTransactionManager
    beforeTest {
        val di = diReceiver()
        cut = di.get()
        rtm = di.get()
    }
    should("olmForgetFallbackKeyAfterRepositoryTest: save, get and delete") {
        rtm.writeTransaction {
            cut.save(1, Instant.fromEpochMilliseconds(24))
            cut.get(1) shouldBe Instant.fromEpochMilliseconds(24)
            cut.save(1, Instant.fromEpochMilliseconds(2424))
            cut.get(1) shouldBe Instant.fromEpochMilliseconds(2424)
            cut.delete(1)
            cut.get(1) shouldBe null
        }
    }
}