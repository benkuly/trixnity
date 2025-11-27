package net.folivo.trixnity.crypto.driver.vodozemac.keys

import net.folivo.trixnity.crypto.driver.keys.PickleKeyFactory
import net.folivo.trixnity.vodozemac.PickleKey

object VodozemacPickleKeyFactory : PickleKeyFactory {
    override fun invoke(value: ByteArray?): VodozemacPickleKey? = value?.let { VodozemacPickleKey(PickleKey(it)) }

    override fun invoke(value: String?): VodozemacPickleKey? = value?.let { VodozemacPickleKey(PickleKey(it)) }

    override fun invoke(): VodozemacPickleKey? = null
}