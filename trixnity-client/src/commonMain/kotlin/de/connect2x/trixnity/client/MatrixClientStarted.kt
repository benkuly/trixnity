package de.connect2x.trixnity.client

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class MatrixClientStarted(internal val delegate: MutableStateFlow<Boolean> = MutableStateFlow(false)) :
    StateFlow<Boolean> by delegate.asStateFlow()