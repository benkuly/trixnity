package net.folivo.trixnity.crypto

import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.core.ByteFlow

data class Sha256ByteFlow(
    private val content: ByteFlow,
    val hash: StateFlow<String?>,
) : ByteFlow by content

expect fun ByteFlow.sha256(): Sha256ByteFlow