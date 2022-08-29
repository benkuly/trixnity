package net.folivo.trixnity.core

import kotlinx.coroutines.CoroutineScope

interface EventHandler {
    fun startInCoroutineScope(scope: CoroutineScope) {}
}