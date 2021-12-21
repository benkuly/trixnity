package net.folivo.trixnity.olm

import io.kotest.matchers.ints.shouldBeGreaterThan
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OlmVersionTest {
    @Test
    fun versionShouldBeSet() = initTest {
        olmVersion.major shouldBeGreaterThan 0
        olmVersion.minor shouldBeGreaterThan 0
        olmVersion.patch shouldBeGreaterThan 0
    }
}