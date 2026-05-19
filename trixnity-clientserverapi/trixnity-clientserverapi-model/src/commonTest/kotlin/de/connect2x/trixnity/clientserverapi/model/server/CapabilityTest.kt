package de.connect2x.trixnity.clientserverapi.model.server

import de.connect2x.trixnity.clientserverapi.model.user.ProfileField
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class CapabilityTest : TrixnityBaseTest() {

    @Test
    fun `Capability - isChangeAllowed - disabled - false`() {
        Capability.ProfileFields(enabled = false).isChangeAllowed(ProfileField.DisplayName) shouldBe false
    }

    @Test
    fun `Capability - isChangeAllowed - allowed is set - does not contains key - false`() {
        Capability.ProfileFields(
            enabled = true,
            allowed = setOf(),
        ).isChangeAllowed(ProfileField.DisplayName) shouldBe false
    }

    @Test
    fun `Capability - isChangeAllowed - allowed is set - contains key - true`() {
        Capability.ProfileFields(
            enabled = true,
            allowed = setOf(ProfileField.DisplayName),
        ).isChangeAllowed(ProfileField.DisplayName) shouldBe true
    }

    @Test
    fun `Capability - isChangeAllowed - disallowed is set - does not contains key - true`() {
        Capability.ProfileFields(
            enabled = true,
            disallowed = setOf(),
        ).isChangeAllowed(ProfileField.DisplayName) shouldBe true
    }

    @Test
    fun `Capability - isChangeAllowed - disallowed is set - contains key - false`() {
        Capability.ProfileFields(
            enabled = true,
            disallowed = setOf(ProfileField.DisplayName),
        ).isChangeAllowed(ProfileField.DisplayName) shouldBe false
    }
}
