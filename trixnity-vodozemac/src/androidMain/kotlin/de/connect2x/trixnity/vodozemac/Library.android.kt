package de.connect2x.trixnity.vodozemac

actual val InitHook: () -> Unit = { System.loadLibrary("vodozemac") }
