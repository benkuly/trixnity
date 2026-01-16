package de.connect2x.trixnity.crypto.core

import kotlinx.coroutines.flow.filterNot
import de.connect2x.trixnity.utils.ByteArrayFlow

fun ByteArrayFlow.filterNotEmpty() = filterNot { it.isEmpty() }