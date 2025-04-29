package net.folivo.trixnity.test.utils

open class TrixnityBaseTest: TrixnityLoggedTest, CoroutineTest {
    override val testScope = CoroutineTestScope()
}