package net.folivo.trixnity.crypto.core

import kotlinx.coroutines.flow.filterNot
import net.folivo.trixnity.utils.ByteArrayFlow

fun ByteArrayFlow.filterNotEmpty() = filterNot { it.isEmpty() }