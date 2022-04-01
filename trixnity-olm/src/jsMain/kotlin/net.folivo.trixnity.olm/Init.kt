package net.folivo.trixnity.olm

import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate

object Init {
    private val isInitialized = MutableStateFlow(false)

    suspend operator fun invoke() {
        if (!isInitialized.getAndUpdate { true }) {
            init().await()
        }
    }
}