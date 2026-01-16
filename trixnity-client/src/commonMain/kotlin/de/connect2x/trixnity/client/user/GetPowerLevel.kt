package de.connect2x.trixnity.client.user

import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent
import de.connect2x.trixnity.core.model.events.m.room.PowerLevelsEventContent

fun interface GetPowerLevel {
    operator fun invoke(
        userId: UserId,
        createEvent: ClientEvent.StateBaseEvent<CreateEventContent>,
        powerLevelsEventContent: PowerLevelsEventContent?
    ): PowerLevel
}

class GetPowerLevelImpl : GetPowerLevel {
    private val legacyRoomVersions = setOf(null, "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11")
    private fun ClientEvent.StateBaseEvent<CreateEventContent>.isCreator(userId: UserId) =
        if (legacyRoomVersions.contains(content.roomVersion)) false
        else (content.additionalCreators.orEmpty() + sender).contains(userId)

    override fun invoke(
        userId: UserId,
        createEvent: ClientEvent.StateBaseEvent<CreateEventContent>,
        powerLevelsEventContent: PowerLevelsEventContent?
    ): PowerLevel {
        return if (createEvent.isCreator(userId)) PowerLevel.Creator
        else {
            if (powerLevelsEventContent == null) {
                if (createEvent.sender == userId) 100 else 0
            } else {
                powerLevelsEventContent.users[userId] ?: powerLevelsEventContent.usersDefault
            }.let { PowerLevel.User(it) }
        }
    }
}