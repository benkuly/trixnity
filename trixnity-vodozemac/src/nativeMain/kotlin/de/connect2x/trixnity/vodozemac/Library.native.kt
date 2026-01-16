@file:OptIn(ExperimentalForeignApi::class)

package de.connect2x.trixnity.vodozemac

import kotlinx.cinterop.ExperimentalForeignApi

actual typealias ExternalSymbolName = SymbolName

internal actual val InitHook: () -> Unit = {}
