@file:OptIn(ExperimentalForeignApi::class)

package net.folivo.trixnity.vodozemac

import kotlinx.cinterop.ExperimentalForeignApi

actual typealias ExternalSymbolName = SymbolName

internal actual val InitHook: () -> Unit = {}
