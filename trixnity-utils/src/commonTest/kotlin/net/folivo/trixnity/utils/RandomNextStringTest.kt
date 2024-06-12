package net.folivo.trixnity.utils

import io.kotest.matchers.string.shouldHaveLength
import kotlin.random.Random
import kotlin.test.Test

class RandomNextStringTest {
    @Test
    fun shouldCreateRandomString() {
        repeat(100) { i ->
            Random.nextString(i) shouldHaveLength i
        }
    }
}