package net.folivo.trixnity.client.api

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

actual fun runBlockingTest(block: suspend () -> Unit): dynamic = GlobalScope.promise { block() }