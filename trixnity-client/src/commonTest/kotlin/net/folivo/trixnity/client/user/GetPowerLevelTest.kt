package net.folivo.trixnity.client.user

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.folivo.trixnity.client.simpleRoom
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent.StateEvent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import net.folivo.trixnity.test.utils.runTest
import kotlin.test.Test

class GetPowerLevelTest : TrixnityBaseTest() {
    private val userId = UserId("me", "server")
    private val roomId = simpleRoom.roomId
    private val cut = GetPowerLevelImpl()

    @Test
    fun `getPowerLevel - is creator`() = runTest {
        cut(
            userId = userId,
            createEvent = createEvent(userId, roomVersion = "12"),
            powerLevelsEventContent = PowerLevelsEventContent( // should be ignored
                users = mapOf(userId to 60L)
            )
        ) shouldBe PowerLevel.Creator
    }

    @Test
    fun `getPowerLevel - is additional creator`() = runTest {
        cut(
            userId = userId,
            createEvent = createEvent(
                UserId("other", "server"),
                roomVersion = "12",
                additionalCreators = setOf(userId)
            ),
            powerLevelsEventContent = PowerLevelsEventContent( // should be ignored
                users = mapOf(userId to 60L)
            )
        ) shouldBe PowerLevel.Creator
    }

    @Test
    fun `getPowerLevel - with power_level event - return entry`() =
        runTest {
            cut(
                userId = userId,
                createEvent = createEvent(UserId("other", "server"), roomVersion = "11"),
                powerLevelsEventContent = PowerLevelsEventContent(
                    users = mapOf(userId to 60L)
                )
            ) shouldBe PowerLevel.User(60)
        }

    @Test
    fun `getPowerLevel - with power_level event - return default`() =
        runTest {
            cut(
                userId = userId,
                createEvent = createEvent(UserId("other", "server"), roomVersion = "11"),
                powerLevelsEventContent = PowerLevelsEventContent(usersDefault = 40)
            ) shouldBe PowerLevel.User(40)
        }

    @Test
    fun `getPowerLevel - without power_level event - return default`() =
        runTest {
            cut(
                userId = userId,
                createEvent = createEvent(UserId("other", "server"), roomVersion = "11"),
                powerLevelsEventContent = null
            ) shouldBe PowerLevel.User(0)
        }


    private fun createEvent(
        creator: UserId,
        roomVersion: String? = null,
        additionalCreators: Set<UserId> = emptySet()
    ) = StateEvent(
        CreateEventContent(roomVersion = roomVersion, additionalCreators = additionalCreators),
        EventId("\$event"),
        creator,
        roomId,
        1234,
        stateKey = ""
    )

    private suspend fun Flow<PowerLevel>.firstPowerLevel() =
        filterIsInstance<PowerLevel.User>().map { it.level }.first()
}