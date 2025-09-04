package net.folivo.trixnity.crypto.driver.libolm.keys

import net.folivo.trixnity.crypto.driver.keys.PickleKeyFactory
import net.folivo.trixnity.utils.encodeUnpaddedBase64

object LibOlmPickleKeyFactory : PickleKeyFactory {
    override fun invoke(value: ByteArray?): LibOlmPickleKey? = value
        ?.let(ByteArray::encodeUnpaddedBase64)
        ?.let(LibOlmPickleKeyFactory::invoke)

    override fun invoke(value: String?): LibOlmPickleKey? = value
        ?.let(::LibOlmPickleKey)

    override fun invoke(): LibOlmPickleKey? = null
}