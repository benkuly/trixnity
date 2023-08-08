package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.repository.OlmAccountRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import org.koin.core.Koin


fun ShouldSpec.olmAccountRepositoryTest(diReceiver: () -> Koin) {
    lateinit var cut: OlmAccountRepository
    lateinit var rtm: RepositoryTransactionManager
    beforeTest {
        val di = diReceiver()
        cut = di.get()
        rtm = di.get()
    }
    should("olmAccountRepositoryTest: save, get and delete") {
        rtm.writeTransaction {
            cut.save(1, "olm")
            cut.get(1) shouldBe "olm"
            cut.save(1, "newOlm")
            cut.get(1) shouldBe "newOlm"
            cut.delete(1)
            cut.get(1) shouldBe null
        }
    }
}