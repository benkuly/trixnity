package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.store.repository.GlobalAccountDataRepository
import net.folivo.trixnity.client.store.repository.RepositoryTransactionManager
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.UnknownGlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import org.koin.core.Koin


fun ShouldSpec.globalAccountDataRepositoryTest(diReceiver: () -> Koin) {
    lateinit var cut: GlobalAccountDataRepository
    lateinit var rtm: RepositoryTransactionManager
    beforeTest {
        val di = diReceiver()
        cut = di.get()
        rtm = di.get()
    }
    should("globalAccountDataRepositoryTest: save, get and delete") {
        val key1 = "m.direct"
        val key2 = "org.example.mynamespace"
        val accountDataEvent1 = GlobalAccountDataEvent(
            DirectEventContent(
                mapOf(
                    UserId(
                        "alice",
                        "server.org"
                    ) to setOf(RoomId("!room", "server"))
                )
            ), ""
        )
        val accountDataEvent2 = GlobalAccountDataEvent(
            UnknownGlobalAccountDataEventContent(
                JsonObject(mapOf("value" to JsonPrimitive("unicorn"))),
                "org.example.mynamespace"
            ),
            ""
        )
        val accountDataEvent3 = GlobalAccountDataEvent(
            UnknownGlobalAccountDataEventContent(
                JsonObject(mapOf("value" to JsonPrimitive("unicorn"))),
                "org.example.mynamespace.2"
            ),
            ""
        )
        val accountDataEvent1Copy = accountDataEvent1.copy(
            content = DirectEventContent(
                mapOf(
                    UserId(
                        "alice",
                        "server.org"
                    ) to null
                )
            )
        )

        rtm.writeTransaction {
            cut.save(key1, "", accountDataEvent1)
            cut.save(key2, "", accountDataEvent2)
            cut.save(key2, "3", accountDataEvent3)
            cut.get(key1, "") shouldBe accountDataEvent1
            cut.get(key2, "") shouldBe accountDataEvent2
            cut.save(key1, "", accountDataEvent1Copy)
            cut.get(key1, "") shouldBe accountDataEvent1Copy
            cut.delete(key1, "")
            cut.get(key1) shouldHaveSize 0
            cut.get(key2) shouldBe mapOf(
                "" to accountDataEvent2,
                "3" to accountDataEvent3
            )
        }
    }
}