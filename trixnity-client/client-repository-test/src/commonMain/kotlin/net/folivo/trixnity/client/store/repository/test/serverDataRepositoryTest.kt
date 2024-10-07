package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.client.store.ServerData
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.ServerDataRepository
import net.folivo.trixnity.clientserverapi.model.media.GetMediaConfig
import net.folivo.trixnity.clientserverapi.model.server.GetVersions
import org.koin.core.Koin

fun ShouldSpec.serverDataRepositoryTest(diReceiver: () -> Koin) {
    lateinit var cut: ServerDataRepository
    lateinit var rtm: RepositoryTransactionManager
    beforeTest {
        val di = diReceiver()
        cut = di.get()
        rtm = di.get()
    }
    should("serverDataRepositoryTest: save, get and delete") {
        val serverData =
            ServerData(GetVersions.Response(listOf("v1.11"), mapOf("features" to true)), GetMediaConfig.Response(1234))
        rtm.writeTransaction {
            cut.save(1, serverData)
            cut.get(1) shouldBe serverData
            val accountCopy = serverData.copy(versions = serverData.versions.copy(listOf("v1.11, v1.24")))
            cut.save(1, accountCopy)
            cut.get(1) shouldBe accountCopy
            cut.delete(1)
            cut.get(1) shouldBe null
        }
    }
}