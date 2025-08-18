package net.folivo.trixnity.client.notification

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class HasSizeMatchTest {
    @Test
    fun `hasSizeMatch - ignore unknown`() {
        hasSizeMatch("dino", 1) shouldBe false
    }

    @Test
    fun `hasSizeMatch - simple number`() {
        hasSizeMatch("1", 1) shouldBe true
        hasSizeMatch("1", 2) shouldBe false
    }

    @Test
    fun `hasSizeMatch - is equal`() {
        hasSizeMatch("==1", 1) shouldBe true
        hasSizeMatch("==1", 2) shouldBe false
    }

    @Test
    fun `hasSizeMatch - less then`() {
        hasSizeMatch("<1", 0) shouldBe true
        hasSizeMatch("<1", 1) shouldBe false
    }

    @Test
    fun `hasSizeMatch - greater then`() {
        hasSizeMatch(">1", 2) shouldBe true
        hasSizeMatch(">1", 1) shouldBe false
    }

    @Test
    fun `hasSizeMatch - less then or equal`() {
        hasSizeMatch("<=1", 1) shouldBe true
        hasSizeMatch("<=1", 2) shouldBe false
    }

    @Test
    fun `hasSizeMatch - greater then or equal`() {
        hasSizeMatch(">=1", 1) shouldBe true
        hasSizeMatch(">=1", 0) shouldBe false
    }

    @Test
    fun `hasSizeMatch - accept reasonable high value`() {
        val maxLong = Long.MAX_VALUE.toString()
        maxLong shouldBe "9223372036854775807"
        hasSizeMatch("<=$maxLong", 1) shouldBe true
        hasSizeMatch("<=9223372036854775808", 1) shouldBe false
    }

    @Test
    fun `hasSizeMatch - ignore missing value`() {
        hasSizeMatch(">=", 1) shouldBe false
        hasSizeMatch(">=", 0) shouldBe false
    }

    @Test
    fun `hasSizeMatch - ignore invalid value`() {
        hasSizeMatch("<=ABC", 1) shouldBe false
        hasSizeMatch("<=ABC", 0) shouldBe false
        hasSizeMatch("<=12345678901234567890", 1) shouldBe false
        hasSizeMatch("<=12345678901234567890", 0) shouldBe false
    }

    @Test
    fun `hasSizeMatch - ignore negative value`() {
        hasSizeMatch(">=-24", 1) shouldBe false
    }

    @Test
    fun `hasSizeMatch - whitespace`() {
        hasSizeMatch(" >= 1 ", 1) shouldBe true
        hasSizeMatch(" >= 1 ", 0) shouldBe false
        hasSizeMatch("\t\t>=\t\t1\t\t", 1) shouldBe true
        hasSizeMatch("\t\t>=\t\t1\t\t", 0) shouldBe false
    }
}
