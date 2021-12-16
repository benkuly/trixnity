package net.folivo.trixnity.client.store

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.folivo.trixnity.client.NoopRepositoryTransactionManager
import net.folivo.trixnity.client.store.repository.RoomUserRepository
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.JOIN
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent.Membership.LEAVE

class RoomUserStoreTest : ShouldSpec({
    val roomUserRepository = mockk<RoomUserRepository>(relaxUnitFun = true)
    lateinit var storeScope: CoroutineScope
    lateinit var cut: RoomUserStore

    beforeTest {
        storeScope = CoroutineScope(Dispatchers.Default)
        cut = RoomUserStore(roomUserRepository, NoopRepositoryTransactionManager, storeScope)
    }
    afterTest {
        clearAllMocks()
        storeScope.cancel()
    }

    val roomId = RoomId("room", "server")
    val aliceId = UserId("alice", "server")
    val bobId = UserId("bob", "server")

    context(RoomUserStore::getAll.name) {
        should("get all users of a room") {
            val scope = CoroutineScope(Dispatchers.Default)
            val aliceUser = mockk<RoomUser>()
            val bobUser = mockk<RoomUser>()
            coEvery { roomUserRepository.get(roomId) }.returns(
                mapOf(
                    aliceId to aliceUser,
                    bobId to bobUser
                )
            )

            cut.getAll(roomId, scope).value shouldContainExactly listOf(aliceUser, bobUser)

            scope.cancel()
        }
    }
    context("get") {
        should("return user from repository") {
            val user = mockk<RoomUser>()
            coEvery { roomUserRepository.getByUserId(aliceId, roomId) } returns user

            cut.get(aliceId, roomId) shouldBe user
        }
        should("prefer cache") {
            val user = mockk<RoomUser>()
            coEvery { roomUserRepository.getByUserId(aliceId, roomId) } returns user

            cut.get(aliceId, roomId) shouldBe user
            cut.get(aliceId, roomId) shouldBe user

            coVerify(exactly = 1) { roomUserRepository.getByUserId(aliceId, roomId) }
        }
    }
    context(RoomUserStore::update.name) {
        should("save to database") {
            val aliceUser = RoomUser(roomId, aliceId, "ALICE", mockk())
            coEvery { roomUserRepository.getByUserId(aliceId, roomId) } returns aliceUser
            coEvery { roomUserRepository.getByUserId(bobId, roomId) } returns null
            cut.update(aliceId, roomId) {
                it shouldBe aliceUser
                it?.copy(name = "ALICE-ALICE")
            }
            cut.update(bobId, roomId) {
                it shouldBe null
                null
            }
            coVerifyAll {
                roomUserRepository.getByUserId(aliceId, roomId)
                roomUserRepository.getByUserId(bobId, roomId)
                roomUserRepository.saveByUserId(aliceId, roomId, aliceUser.copy(name = "ALICE-ALICE"))
                roomUserRepository.deleteByUserId(bobId, roomId)
            }
        }
        should("delete in database") {
            val aliceUser = RoomUser(roomId, aliceId, "ALICE", mockk())
            coEvery { roomUserRepository.getByUserId(aliceId, roomId) } returns aliceUser
            cut.update(aliceId, roomId) {
                null
            }
            coVerifyAll {
                roomUserRepository.getByUserId(aliceId, roomId)
                roomUserRepository.deleteByUserId(aliceId, roomId)
            }
        }
        should("prefer cache") {
            val aliceUser = RoomUser(roomId, aliceId, "ALICE", mockk())
            coEvery { roomUserRepository.getByUserId(aliceId, roomId) } returns aliceUser
            cut.update(aliceId, roomId) {
                it shouldBe aliceUser.copy(name = "ALICE")
                it?.copy(name = "ALICE-ALICE")
            }
            cut.update(aliceId, roomId) {
                it shouldBe aliceUser.copy(name = "ALICE-ALICE")
                it?.copy(name = "ALICE-ALICE")
            }
            coVerifyAll {
                roomUserRepository.getByUserId(aliceId, roomId)
                roomUserRepository.saveByUserId(aliceId, roomId, aliceUser.copy(name = "ALICE-ALICE"))
            }
        }
    }
    context(RoomUserStore::getByOriginalNameAndMembership.name) {
        should("return matching userIds") {
            val user1 = mockk<RoomUser> {
                every { event } returns mockk {
                    every { content } returns mockk {
                        every { membership } returns JOIN
                        every { displayName } returns "A"
                    }
                }
            }
            val user2 = mockk<RoomUser> {
                every { event } returns mockk {
                    every { content } returns mockk {
                        every { membership } returns JOIN
                        every { displayName } returns "A"
                    }
                }
            }
            val user3 = mockk<RoomUser> {
                every { event } returns mockk {
                    every { content } returns mockk {
                        every { membership } returns JOIN
                        every { displayName } returns "B"
                    }
                }
            }
            val user4 = mockk<RoomUser> {
                every { event } returns mockk {
                    every { content } returns mockk {
                        every { membership } returns LEAVE
                        every { displayName } returns "A"
                    }
                }
            }
            coEvery { roomUserRepository.get(roomId) }.returns(
                mapOf(
                    UserId("user1", "server") to user1,
                    UserId("user2", "server") to user2,
                    UserId("user3", "server") to user3,
                    UserId("user4", "server") to user4
                )
            )

            cut.getByOriginalNameAndMembership("A", setOf(JOIN), roomId) shouldContainExactly listOf(
                UserId("user1", "server"),
                UserId("user2", "server")
            )
        }
    }
})