package de.connect2x.trixnity.crypto.driver.vodozemac.keys

import de.connect2x.trixnity.crypto.driver.keys.PickleKey
import kotlin.jvm.JvmInline
import de.connect2x.trixnity.vodozemac.PickleKey as Inner

@JvmInline
value class VodozemacPickleKey(val inner: Inner) : PickleKey