package net.folivo.trixnity.examples.clientserverapi.client.ping

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

fun main() {
    GlobalScope.promise { example() }
}