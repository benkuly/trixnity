package net.folivo.trixnity.test.utils

internal actual val runningUnderKarma: Boolean
    get() = js("typeof window !== 'undefined' && typeof window.__karma__ !== 'undefined'") as Boolean