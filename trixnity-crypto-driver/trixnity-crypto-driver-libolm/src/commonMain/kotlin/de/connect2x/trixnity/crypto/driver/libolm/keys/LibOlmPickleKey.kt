package de.connect2x.trixnity.crypto.driver.libolm.keys

import de.connect2x.trixnity.crypto.driver.keys.PickleKey
import kotlin.jvm.JvmInline

@JvmInline
value class LibOlmPickleKey(internal val inner: String) : PickleKey