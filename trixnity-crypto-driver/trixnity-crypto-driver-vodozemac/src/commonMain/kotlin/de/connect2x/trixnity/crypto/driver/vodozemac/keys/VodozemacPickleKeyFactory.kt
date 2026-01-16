package de.connect2x.trixnity.crypto.driver.vodozemac.keys

import de.connect2x.trixnity.crypto.driver.keys.PickleKeyFactory
import de.connect2x.trixnity.vodozemac.PickleKey

object VodozemacPickleKeyFactory : PickleKeyFactory {
    override fun invoke(value: ByteArray?): VodozemacPickleKey? = value?.let { VodozemacPickleKey(PickleKey(it)) }

    override fun invoke(value: String?): VodozemacPickleKey? = value?.let { VodozemacPickleKey(PickleKey(it)) }

    override fun invoke(): VodozemacPickleKey? = null
}