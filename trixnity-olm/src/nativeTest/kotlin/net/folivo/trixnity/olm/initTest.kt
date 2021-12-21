package net.folivo.trixnity.olm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
actual fun initTest(block: suspend () -> Unit) = runTest { block() }