package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.folivo.trixnity.client.store.repository.test.buildTestDatabase
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.UnknownGlobalAccountDataEventContent
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.secretstorage.SecretKeyEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomGlobalAccountDataRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomGlobalAccountDataRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomGlobalAccountDataRepository(db, createMatrixEventJson())
    }

    @Test
    fun `Save, get and delete`() = runTest {
        val key1 = "m.direct"
        val key2 = "org.example.mynamespace"
        val accountDataEvent1 = mapOf(
            "" to Event.GlobalAccountDataEvent(
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
            "" to Event.GlobalAccountDataEvent(
                UnknownGlobalAccountDataEventContent(
                    JsonObject(mapOf("value" to JsonPrimitive("unicorn"))),
                    "org.example.mynamespace"
                ),
                ""
            )
        )
        val accountDataEvent1Copy = mapOf(
            "" to accountDataEvent1[""].shouldNotBeNull().copy(
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

        repo.save(key1, accountDataEvent1)
        repo.save(key2, accountDataEvent2)
        repo.get(key1) shouldBe accountDataEvent1
        repo.get(key2) shouldBe accountDataEvent2
        repo.save(key1, accountDataEvent1Copy)
        repo.get(key1) shouldBe accountDataEvent1Copy
        repo.delete(key1)
        repo.get(key1) shouldHaveSize 0
    }

    @Test
    fun `Save and get by second key`() = runTest {
        val key = "m.secret_storage.key"
        val accountDataEvent = Event.GlobalAccountDataEvent(
            SecretKeyEventContent.AesHmacSha2Key("name"), "key"
        )
        repo.saveBySecondKey(key, secondKey = "key", accountDataEvent)
        repo.getBySecondKey(key, "key") shouldBe accountDataEvent
    }
}
