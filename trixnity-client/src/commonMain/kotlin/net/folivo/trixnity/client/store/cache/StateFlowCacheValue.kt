package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow

internal data class StateFlowCacheValue<T>(
    val value: MutableStateFlow<T>,
    val subscribers: Set<CoroutineScope>,
    val removerJob: Job?
)