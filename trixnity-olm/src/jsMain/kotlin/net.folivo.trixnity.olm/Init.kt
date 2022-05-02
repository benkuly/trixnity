package net.folivo.trixnity.olm

import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate


private val initializeStarted = MutableStateFlow(false)
private val isInitialized = MutableStateFlow(false)

suspend fun initOlm() {
    if (!initializeStarted.getAndUpdate { true }) {
        init().await()
        isInitialized.value = true
    } else isInitialized.first { it }
}