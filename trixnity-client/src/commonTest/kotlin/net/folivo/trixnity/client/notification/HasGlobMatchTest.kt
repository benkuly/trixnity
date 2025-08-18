package net.folivo.trixnity.client.notification

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class HasGlobMatchTest {
    @Test
    fun `hasGlobMatch - no match`() {
        hasGlobMatch("dino", "unicorn") shouldBe false
    }

    @Test
    fun `hasGlobMatch - match substring`() {
        hasGlobMatch("dino", "ino") shouldBe true
    }

    @Test
    fun `hasGlobMatch - match glob`() {
        hasGlobMatch("do", "d*o") shouldBe true
        hasGlobMatch("dino", "d*o") shouldBe true
        hasGlobMatch("dino", "di?o") shouldBe true
        hasGlobMatch("dino", "di**o") shouldBe true
    }

    @Test
    fun `hasGlobMatch - no match glob`() {
        hasGlobMatch("din", "d*o") shouldBe false
        hasGlobMatch("ditto", "di?o") shouldBe false
        hasGlobMatch("dio", "di?o") shouldBe false
    }

    @Test
    fun `hasGlobMatch - ignore other regex commands`() {
        val dangerousChars = listOf(
            ".", "\\", "+", "(", ")", "[", "]", "{", "}", "^", "$", "|"
        )

        for (char in dangerousChars) {
            withClue("char: $char") {
                hasGlobMatch("di${char}no", "di${char}no") shouldBe true
                hasGlobMatch("dino", "di${char}no") shouldBe false
            }
        }
    }
}
