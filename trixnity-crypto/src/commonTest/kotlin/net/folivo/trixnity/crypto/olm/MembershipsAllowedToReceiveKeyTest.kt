package net.folivo.trixnity.crypto.olm

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent.HistoryVisibility
import net.folivo.trixnity.core.model.events.m.room.Membership

class MembershipsAllowedToReceiveKeyTest : ShouldSpec({
    should("allow [Membership.JOIN] when HistoryVisibility.JOINED") {
        HistoryVisibility.JOINED.membershipsAllowedToReceiveKey shouldBe
                setOf(Membership.JOIN)
    }
    should("allow [Membership.JOIN, Membership.INVITE] when HistoryVisibility.INVITED") {
        HistoryVisibility.INVITED.membershipsAllowedToReceiveKey shouldBe
                setOf(Membership.JOIN, Membership.INVITE)
    }
    should("allow [Membership.JOIN, Membership.INVITE, Membership.KNOCK] when HistoryVisibility.SHARED") {
        HistoryVisibility.SHARED.membershipsAllowedToReceiveKey shouldBe
                setOf(Membership.JOIN, Membership.INVITE, Membership.KNOCK)
    }
    should("allow [Membership.JOIN, Membership.INVITE, Membership.KNOCK] when HistoryVisibility.WORLD_READABLE") {
        HistoryVisibility.WORLD_READABLE.membershipsAllowedToReceiveKey shouldBe
                setOf(Membership.JOIN, Membership.INVITE, Membership.KNOCK)
    }
    should("allow [Membership.JOIN] when HistoryVisibility is null") {
        val historyVisibility: HistoryVisibility? = null
        historyVisibility.membershipsAllowedToReceiveKey shouldBe
                setOf(Membership.JOIN)
    }
})