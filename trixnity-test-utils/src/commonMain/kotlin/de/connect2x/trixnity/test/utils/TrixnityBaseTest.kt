package de.connect2x.trixnity.test.utils

open class TrixnityBaseTest: TrixnityLoggedTest, CoroutineTest {
    override val testScope = CoroutineTestScope()
}