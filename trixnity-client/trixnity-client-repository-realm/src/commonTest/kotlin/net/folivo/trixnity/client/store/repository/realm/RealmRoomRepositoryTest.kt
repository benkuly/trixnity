package net.folivo.trixnity.client.store.repository.realm

import com.benasher44.uuid.uuid4
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import net.folivo.trixnity.client.store.Room
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.serialization.createMatrixEventJson


class RealmRoomRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var realm: Realm
    lateinit var cut: RealmRoomRepository

    beforeTest {
        val realmDbPath = "build/test-db/${uuid4()}"
        realm = Realm.open(
            RealmConfiguration.Builder(
                schema = setOf(
                    RealmRoom::class,
                )
            ).apply { directory(realmDbPath) }.build()
        )

        cut = RealmRoomRepository(createMatrixEventJson())
    }
    should("save, get and delete") {
        val key1 = RoomId("room1", "server")
        val key2 = RoomId("room2", "server")
        val room1 = Room(key1, lastEventId = null)
        val room2 = Room(key2, lastEventId = null)
        val room2Copy = room2.copy(lastEventId = EventId("\$Event2"))

        writeTransaction(realm) {
            cut.save(key1, room1)
            cut.save(key2, room2)
            cut.get(key1) shouldBe room1
            cut.get(key2) shouldBe room2
            cut.save(key2, room2Copy)
            cut.get(key2) shouldBe room2Copy
            cut.delete(key1)
            cut.get(key1) shouldBe null
        }
    }
    should("get all") {
        val key1 = RoomId("room1", "server")
        val key2 = RoomId("room2", "server")
        val room1 = Room(key1, lastEventId = null)
        val room2 = Room(key1, lastEventId = null)

        writeTransaction(realm) {
            cut.save(key1, room1)
            cut.save(key2, room2)
            cut.getAll() shouldContainAll listOf(room1, room2)
        }
    }
})