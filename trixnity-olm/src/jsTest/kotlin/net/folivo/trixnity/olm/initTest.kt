package net.folivo.trixnity.olm

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

actual fun initTest(block: suspend () -> Unit): dynamic = GlobalScope.promise {
    Init()
    block()
}