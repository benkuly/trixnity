package net.folivo.trixnity.client.notification

import io.kotest.matchers.shouldBe
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.Test

class HasSizeMatchTest : TrixnityBaseTest() {
    @Test
    fun `ignore unknown`() {
        hasSizeMatch("dino", 1) shouldBe false
    }

    @Test
    fun `simple number`() {
        hasSizeMatch("1", 1) shouldBe true
        hasSizeMatch("1", 2) shouldBe false
    }

    @Test
    fun `is equal`() {
        hasSizeMatch("==1", 1) shouldBe true
        hasSizeMatch("==1", 2) shouldBe false
    }

    @Test
    fun `less then`() {
        hasSizeMatch("<1", 0) shouldBe true
        hasSizeMatch("<1", 1) shouldBe false
    }

    @Test
    fun `greater then`() {
        hasSizeMatch(">1", 2) shouldBe true
        hasSizeMatch(">1", 1) shouldBe false
    }

    @Test
    fun `less then or equal`() {
        hasSizeMatch("<=1", 1) shouldBe true
        hasSizeMatch("<=1", 2) shouldBe false
    }

    @Test
    fun `greater then or equal`() {
        hasSizeMatch(">=1", 1) shouldBe true
        hasSizeMatch(">=1", 0) shouldBe false
    }

    @Test
    fun `accept reasonable high value`() {
        val maxLong = Long.MAX_VALUE.toString()
        maxLong shouldBe "9223372036854775807"
        hasSizeMatch("<=$maxLong", 1) shouldBe true
        hasSizeMatch("<=9223372036854775808", 1) shouldBe false
    }

    @Test
    fun `ignore missing value`() {
        hasSizeMatch(">=", 1) shouldBe false
        hasSizeMatch(">=", 0) shouldBe false
    }

    @Test
    fun `ignore invalid value`() {
        hasSizeMatch("<=ABC", 1) shouldBe false
        hasSizeMatch("<=ABC", 0) shouldBe false
        hasSizeMatch("<=12345678901234567890", 1) shouldBe false
        hasSizeMatch("<=12345678901234567890", 0) shouldBe false
    }

    @Test
    fun `ignore negative value`() {
        hasSizeMatch(">=-24", 1) shouldBe false
    }

    @Test
    fun `whitespace`() {
        hasSizeMatch(" >= 1 ", 1) shouldBe true
        hasSizeMatch(" >= 1 ", 0) shouldBe false
        hasSizeMatch("\t\t>=\t\t1\t\t", 1) shouldBe true
        hasSizeMatch("\t\t>=\t\t1\t\t", 0) shouldBe false
    }
}
