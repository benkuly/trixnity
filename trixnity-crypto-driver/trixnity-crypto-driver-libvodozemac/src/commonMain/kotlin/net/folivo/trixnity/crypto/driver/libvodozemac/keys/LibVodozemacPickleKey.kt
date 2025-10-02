package net.folivo.trixnity.crypto.driver.libvodozemac.keys

import net.folivo.trixnity.crypto.driver.keys.PickleKey
import net.folivo.trixnity.vodozemac.PickleKey as Inner
import kotlin.jvm.JvmInline

@JvmInline
value class LibVodozemacPickleKey(val inner: Inner) : PickleKey