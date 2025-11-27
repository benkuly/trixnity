package net.folivo.trixnity.crypto.driver.vodozemac.keys

import net.folivo.trixnity.crypto.driver.keys.PickleKey
import net.folivo.trixnity.vodozemac.PickleKey as Inner
import kotlin.jvm.JvmInline

@JvmInline
value class VodozemacPickleKey(val inner: Inner) : PickleKey