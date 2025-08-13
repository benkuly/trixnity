package net.folivo.trixnity.client.user

import net.folivo.trixnity.core.UserInfo
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent

interface CanDoAction {
    fun toUser(
        otherUserId: UserId,
        createEvent: ClientEvent.StateBaseEvent<CreateEventContent>,
        powerLevelsEventContent: PowerLevelsEventContent?,
        actionCheck: (ownPowerLevel: Long) -> Boolean
    ): Boolean

    fun asUser(
        userId: UserId,
        createEvent: ClientEvent.StateBaseEvent<CreateEventContent>,
        powerLevelsEventContent: PowerLevelsEventContent?,
        actionCheck: (userPowerLevel: Long) -> Boolean
    ): Boolean
}

class CanDoActionImpl(
    private val userInfo: UserInfo,
    private val getPowerLevel: GetPowerLevel,
) : CanDoAction {
    override fun toUser(
        otherUserId: UserId,
        createEvent: ClientEvent.StateBaseEvent<CreateEventContent>,
        powerLevelsEventContent: PowerLevelsEventContent?,
        actionCheck: (ownPowerLevel: Long) -> Boolean
    ): Boolean =
        when (val otherPowerLevel = getPowerLevel(otherUserId, createEvent, powerLevelsEventContent)) {
            is PowerLevel.Creator -> false
            is PowerLevel.User ->
                when (val ownPowerLevel = getPowerLevel(userInfo.userId, createEvent, powerLevelsEventContent)) {
                    is PowerLevel.Creator -> true
                    is PowerLevel.User -> ownPowerLevel.level > otherPowerLevel.level && actionCheck(ownPowerLevel.level)
                }
        }

    override fun asUser(
        userId: UserId,
        createEvent: ClientEvent.StateBaseEvent<CreateEventContent>,
        powerLevelsEventContent: PowerLevelsEventContent?,
        actionCheck: (userPowerLevel: Long) -> Boolean
    ): Boolean =
        when (val powerLevel = getPowerLevel(userId, createEvent, powerLevelsEventContent)) {
            is PowerLevel.Creator -> true
            is PowerLevel.User -> actionCheck(powerLevel.level)
        }
}