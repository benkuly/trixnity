package net.folivo.trixnity.crypto.olm

import io.kotest.matchers.shouldBe
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent.HistoryVisibility
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.Test

class MembershipsAllowedToReceiveKeyTest : TrixnityBaseTest() {

    @Test
    fun `allow JOIN when HistoryVisibility JOINED`() {
        HistoryVisibility.JOINED.membershipsAllowedToReceiveKey shouldBe
                setOf(Membership.JOIN)
    }

    @Test
    fun `allow JOIN INVITE when HistoryVisibility INVITED`() {
        HistoryVisibility.INVITED.membershipsAllowedToReceiveKey shouldBe
                setOf(Membership.JOIN, Membership.INVITE)
    }

    @Test
    fun `allow JOIN INVITE KNOCK when HistoryVisibility SHARED`() {
        HistoryVisibility.SHARED.membershipsAllowedToReceiveKey shouldBe
                setOf(Membership.JOIN, Membership.INVITE, Membership.KNOCK)
    }

    @Test
    fun `allow JOIN INVITE KNOCK when HistoryVisibility WORLD_READABLE`() {
        HistoryVisibility.WORLD_READABLE.membershipsAllowedToReceiveKey shouldBe
                setOf(Membership.JOIN, Membership.INVITE, Membership.KNOCK)
    }

    @Test
    fun `allow JOIN when HistoryVisibility is null`() {
        val historyVisibility: HistoryVisibility? = null
        historyVisibility.membershipsAllowedToReceiveKey shouldBe
                setOf(Membership.JOIN)
    }
}