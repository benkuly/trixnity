package net.folivo.trixnity.crypto.driver.libvodozemac.keys

import net.folivo.trixnity.crypto.driver.keys.PickleKeyFactory
import net.folivo.trixnity.vodozemac.PickleKey

object LibVodozemacPickleKeyFactory : PickleKeyFactory {
    override fun invoke(value: ByteArray?): LibVodozemacPickleKey?
        = value?.let { LibVodozemacPickleKey(PickleKey(it)) }

    override fun invoke(value: String?): LibVodozemacPickleKey?
        = value?.let { LibVodozemacPickleKey(PickleKey(it)) }

    override fun invoke(): LibVodozemacPickleKey?
        = null
}