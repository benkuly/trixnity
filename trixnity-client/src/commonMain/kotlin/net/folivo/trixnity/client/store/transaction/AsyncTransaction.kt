package net.folivo.trixnity.client.store.transaction

import kotlinx.coroutines.flow.MutableStateFlow

data class AsyncTransaction(
    val id: String,
    val operations: List<suspend () -> Unit>,
    val transactionHasBeenApplied: MutableStateFlow<Boolean>,
    val onRollback: suspend () -> Unit,
)