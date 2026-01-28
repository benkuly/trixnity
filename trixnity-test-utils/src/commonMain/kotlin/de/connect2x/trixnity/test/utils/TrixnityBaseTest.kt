package de.connect2x.trixnity.test.utils

open class TrixnityBaseTest : LoggedTest, CoroutineTest {
    override val testScope = CoroutineTestScope()
}