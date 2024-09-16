package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.ServerVersions
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.ServerVersionsRepository
import org.koin.core.Koin

fun ShouldSpec.serverVersionsRepositoryTest(diReceiver: () -> Koin) {
    lateinit var cut: ServerVersionsRepository
    lateinit var rtm: RepositoryTransactionManager
    beforeTest {
        val di = diReceiver()
        cut = di.get()
        rtm = di.get()
    }
    should("serverVersionsRepositoryTest: save, get and delete") {
        val serverVersions = ServerVersions(listOf("v1.11"), mapOf("feature" to true))
        rtm.writeTransaction {
            cut.save(1, serverVersions)
            cut.get(1) shouldBe serverVersions
            val accountCopy = serverVersions.copy(listOf("v1.11, v1.24"))
            cut.save(1, accountCopy)
            cut.get(1) shouldBe accountCopy
            cut.delete(1)
            cut.get(1) shouldBe null
        }
    }
}