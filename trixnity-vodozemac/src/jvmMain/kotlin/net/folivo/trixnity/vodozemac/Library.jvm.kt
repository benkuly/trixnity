package net.folivo.trixnity.vodozemac

actual val InitHook: () -> Unit = { NativeLoader.ensureLoaded() }
