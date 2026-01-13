package net.folivo.trixnity.vodozemac

actual val InitHook: () -> Unit = { JvmLoader.load("vodozemac") }
