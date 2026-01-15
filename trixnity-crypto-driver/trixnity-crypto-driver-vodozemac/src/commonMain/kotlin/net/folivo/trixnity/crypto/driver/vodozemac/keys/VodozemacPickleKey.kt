package net.folivo.trixnity.crypto.driver.vodozemac.keys

import net.folivo.trixnity.crypto.driver.keys.PickleKey
import kotlin.jvm.JvmInline
import net.folivo.trixnity.vodozemac.PickleKey as Inner

@JvmInline
value class VodozemacPickleKey(val inner: Inner) : PickleKey