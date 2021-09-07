package net.folivo.trixnity.olm

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.coroutines.await

object Init {
    private val isInitialized = atomic(false)

    suspend operator fun invoke() {
        isInitialized.getAndUpdate {
            if (!it) init().await()
            true
        }
    }
}