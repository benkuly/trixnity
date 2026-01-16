package de.connect2x.trixnity.crypto.driver.libolm.keys

import de.connect2x.trixnity.crypto.driver.keys.PickleKeyFactory
import de.connect2x.trixnity.utils.encodeUnpaddedBase64

object LibOlmPickleKeyFactory : PickleKeyFactory {
    override fun invoke(value: ByteArray?): LibOlmPickleKey? = value
        ?.let(ByteArray::encodeUnpaddedBase64)
        ?.let(LibOlmPickleKeyFactory::invoke)

    override fun invoke(value: String?): LibOlmPickleKey? = value
        ?.let(::LibOlmPickleKey)

    override fun invoke(): LibOlmPickleKey? = null
}