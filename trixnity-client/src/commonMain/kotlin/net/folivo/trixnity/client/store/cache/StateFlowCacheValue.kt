package net.folivo.trixnity.client.store.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration

data class StateFlowCacheValue<T>(
    val value: MutableStateFlow<T>,
    val subscribers: Set<CoroutineScope>,
    val removeTimer: MutableSharedFlow<Duration>,
    val removerJob: Job
)