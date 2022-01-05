package net.folivo.trixnity.client.store.exposed

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event.GlobalAccountDataEvent
import net.folivo.trixnity.core.model.events.UnknownGlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.serialization.createMatrixJson
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class ExposedGlobalAccountDataRepositoryTest : ShouldSpec({
    lateinit var cut: ExposedGlobalAccountDataRepository
    beforeTest {
        createDatabase()
        newSuspendedTransaction {
            SchemaUtils.create(ExposedGlobalAccountData)
        }
        cut = ExposedGlobalAccountDataRepository(createMatrixJson())
    }
    should("save, get and delete") {
        val key1 = "m.direct"
        val key2 = "org.example.mynamespace"
        val accountDataEvent1 = mapOf(
            "" to GlobalAccountDataEvent(
                DirectEventContent(
                    mapOf(
                        UserId(
                            "alice",
                            "server.org"
                        ) to setOf(RoomId("!room", "server"))
                    )
                ), ""
            )
        )
        val accountDataEvent2 = mapOf(
            "" to GlobalAccountDataEvent(
                UnknownGlobalAccountDataEventContent(
                    JsonObject(mapOf("value" to JsonPrimitive("unicorn"))),
                    "org.example.mynamespace"
                ),
                ""
            )
        )
        val accountDataEvent1Copy = mapOf(
            "" to accountDataEvent1[""]!!.copy(
                content = DirectEventContent(
                    mapOf(
                        UserId(
                            "alice",
                            "server.org"
                        ) to null
                    )
                )
            )
        )

        newSuspendedTransaction {
            cut.save(key1, accountDataEvent1)
            cut.save(key2, accountDataEvent2)
            cut.get(key1) shouldBe accountDataEvent1
            cut.get(key2) shouldBe accountDataEvent2
            cut.save(key1, accountDataEvent1Copy)
            cut.get(key1) shouldBe accountDataEvent1Copy
            cut.delete(key1)
            cut.get(key1) shouldHaveSize 0
        }
    }
    should("save and get by second key") {
        val key = "m.secret_storage.key"
        val accountDataEvent = GlobalAccountDataEvent(
            SecretKeyEventContent.AesHmacSha2Key("name"), "key"
        )
        newSuspendedTransaction {
            cut.saveBySecondKey(key, "key", accountDataEvent)
            cut.getBySecondKey(key, "key") shouldBe accountDataEvent
        }
    }
})