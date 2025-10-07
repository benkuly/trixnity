package net.folivo.trixnity.crypto.driver.libolm.keys

import net.folivo.trixnity.crypto.driver.keys.PickleKey
import kotlin.jvm.JvmInline

@JvmInline
value class LibOlmPickleKey(internal val inner: String) : PickleKey