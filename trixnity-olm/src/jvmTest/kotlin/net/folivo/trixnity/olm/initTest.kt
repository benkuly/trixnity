package net.folivo.trixnity.olm

import kotlinx.coroutines.runBlocking

actual fun initTest(block: suspend () -> Unit) = runBlocking { block() }