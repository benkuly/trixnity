package net.folivo.trixnity.client.notification

import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.push.PushCondition
import net.folivo.trixnity.core.model.push.PushRule

internal fun getRoomsWithDisabledPushRules(pushRules: List<PushRule>) =
    pushRules.mapNotNull { pushRule ->
        (pushRule as? PushRule.Override)
            ?.takeIf { it.actions.isEmpty() }
            ?.conditions
            ?.takeIf { it.size == 1 }?.first()
            ?.let { it as? PushCondition.EventMatch }
            ?.takeIf { it.key == "room_id" }
            ?.pattern
            ?.let(::RoomId)
    }.toSet()