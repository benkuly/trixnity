package net.folivo.trixnity.olm

import io.kotest.matchers.ints.shouldBeGreaterThan
import kotlin.test.Test

class OlmVersionTest {
    @Test
    fun versionShouldBeSet() = initTest {
        olmVersion.major shouldBeGreaterThan 0
        olmVersion.minor shouldBeGreaterThan 0
        olmVersion.patch shouldBeGreaterThan 0
    }
}