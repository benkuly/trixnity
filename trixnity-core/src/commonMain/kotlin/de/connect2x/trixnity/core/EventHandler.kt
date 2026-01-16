package de.connect2x.trixnity.core

import kotlinx.coroutines.CoroutineScope

interface EventHandler {
    fun startInCoroutineScope(scope: CoroutineScope) {}
}