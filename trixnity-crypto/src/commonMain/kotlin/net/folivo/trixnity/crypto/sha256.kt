package net.folivo.trixnity.crypto

import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.core.ByteArrayFlow

data class Sha256ByteFlow(
    private val content: ByteArrayFlow,
    val hash: StateFlow<String?>,
) : ByteArrayFlow by content

expect fun ByteArrayFlow.sha256(): Sha256ByteFlow