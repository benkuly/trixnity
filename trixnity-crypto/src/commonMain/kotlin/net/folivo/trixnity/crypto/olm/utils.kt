package net.folivo.trixnity.crypto.olm

import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent.HistoryVisibility
import net.folivo.trixnity.core.model.events.m.room.Membership

val HistoryVisibility?.membershipsAllowedToReceiveKey: Set<Membership>
    get() = when (this) {
        HistoryVisibility.INVITED -> setOf(Membership.INVITE)
        HistoryVisibility.JOINED -> setOf(Membership.JOIN)
        HistoryVisibility.SHARED,
        HistoryVisibility.WORLD_READABLE,
        -> setOf(Membership.JOIN, Membership.INVITE, Membership.KNOCK)

        null -> setOf(Membership.JOIN)
    }