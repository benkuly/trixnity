package net.folivo.trixnity.crypto.core

import kotlinx.coroutines.flow.StateFlow
import net.folivo.trixnity.utils.ByteArrayFlow

class Sha256ByteFlow(
    content: ByteArrayFlow,
    val hash: StateFlow<String?>,
) : ByteArrayFlow by content

expect fun ByteArrayFlow.sha256(): Sha256ByteFlow