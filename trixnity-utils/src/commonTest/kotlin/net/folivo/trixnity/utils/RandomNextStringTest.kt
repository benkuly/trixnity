package net.folivo.trixnity.utils

import io.kotest.matchers.string.shouldHaveLength
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.random.Random
import kotlin.test.Test

class RandomNextStringTest : TrixnityBaseTest() {
    @Test
    fun shouldCreateRandomString() {
        repeat(1_000) { i ->
            Random.nextString(i) shouldHaveLength i
        }
    }
}