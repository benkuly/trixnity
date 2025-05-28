package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import net.folivo.trixnity.client.store.UserPresence
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.UserPresenceRepository
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.Presence
import org.koin.core.Koin


fun ShouldSpec.userPresenceRepositoryTest(diReceiver: () -> Koin) {
    lateinit var cut: UserPresenceRepository
    lateinit var rtm: RepositoryTransactionManager
    beforeTest {
        val di = diReceiver()
        cut = di.get()
        rtm = di.get()
    }
    should("userPresenceRepositoryTest: save, get and delete") {
        val key1 = UserId("user1", "server")
        val key2 = UserId("user2", "server")
        val userPresence1 = UserPresence(Presence.OFFLINE, Clock.System.now())
        val userPresence2 = UserPresence(Presence.ONLINE, Clock.System.now())
        val userPresence2Copy = userPresence2.copy(statusMessage = "status")

        rtm.writeTransaction {
            cut.save(key1, userPresence1)
            cut.save(key2, userPresence2)
            cut.get(key1) shouldBe userPresence1
            cut.get(key2) shouldBe userPresence2
            cut.save(key2, userPresence2Copy)
            cut.get(key2) shouldBe userPresence2Copy
            cut.delete(key1)
            cut.get(key1) shouldBe null
        }
    }
}