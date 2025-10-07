package net.folivo.trixnity.vodozemac

actual val InitHook: () -> Unit = { System.loadLibrary("vodozemac") }
