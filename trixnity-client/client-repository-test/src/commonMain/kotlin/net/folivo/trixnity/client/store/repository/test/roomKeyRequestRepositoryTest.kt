package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import net.folivo.trixnity.client.store.StoredRoomKeyRequest
import net.folivo.trixnity.client.store.repository.RoomKeyRequestRepository
import net.folivo.trixnity.client.store.transaction.RepositoryTransactionManager
import net.folivo.trixnity.core.model.events.m.KeyRequestAction
import net.folivo.trixnity.core.model.events.m.RoomKeyRequestEventContent
import org.koin.core.Koin


fun ShouldSpec.roomKeyRequestRepositoryTest(diReceiver: () -> Koin) {
    lateinit var cut: RoomKeyRequestRepository
    lateinit var rtm: RepositoryTransactionManager
    beforeTest {
        val di = diReceiver()
        cut = di.get()
        rtm = di.get()
    }
    should("roomKeyRequestRepositoryTest: save, get and delete") {
        val key1 = "key1"
        val key2 = "key2"
        val roomKeyRequest1 = StoredRoomKeyRequest(
            RoomKeyRequestEventContent(KeyRequestAction.REQUEST, "A", "r1"),
            setOf("DEV1", "DEV2"),
            Instant.fromEpochMilliseconds(1234)
        )
        val roomKeyRequest2 = StoredRoomKeyRequest(
            RoomKeyRequestEventContent(KeyRequestAction.REQUEST, "A", "r2"),
            setOf("DEV1"),
            Instant.fromEpochMilliseconds(23)
        )
        val roomKeyRequest2Copy = roomKeyRequest2.copy(createdAt = Instant.fromEpochMilliseconds(24))

        rtm.writeTransaction {
            cut.save(key1, roomKeyRequest1)
            cut.save(key2, roomKeyRequest2)
            cut.get(key1) shouldBe roomKeyRequest1
            cut.get(key2) shouldBe roomKeyRequest2
            cut.save(key2, roomKeyRequest2Copy)
            cut.get(key2) shouldBe roomKeyRequest2Copy
            cut.delete(key1)
            cut.get(key1) shouldBe null
        }
    }
    should("roomKeyRequestRepositoryTest: get all") {
        val key1 = "key1"
        val key2 = "key2"
        val roomKeyRequest1 = StoredRoomKeyRequest(
            RoomKeyRequestEventContent(KeyRequestAction.REQUEST, "A", "r1"),
            setOf("DEV1", "DEV2"),
            Instant.fromEpochMilliseconds(1234)
        )
        val roomKeyRequest2 = StoredRoomKeyRequest(
            RoomKeyRequestEventContent(KeyRequestAction.REQUEST, "A", "r2"),
            setOf("DEV1"),
            Instant.fromEpochMilliseconds(23)
        )

        rtm.writeTransaction {
            cut.save(key1, roomKeyRequest1)
            cut.save(key2, roomKeyRequest2)
            cut.getAll() shouldContainAll listOf(roomKeyRequest1, roomKeyRequest2)
        }
    }
}