package net.folivo.trixnity.examples.multiplatform

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

fun main() {
    GlobalScope.promise { example() }
}