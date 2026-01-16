package de.connect2x.trixnity.vodozemac

actual val InitHook: () -> Unit = { JvmLoader.load("vodozemac") }
