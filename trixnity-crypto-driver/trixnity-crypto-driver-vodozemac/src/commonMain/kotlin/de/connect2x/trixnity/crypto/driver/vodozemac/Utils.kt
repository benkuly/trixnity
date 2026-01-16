package de.connect2x.trixnity.crypto.driver.vodozemac

import kotlin.io.encoding.Base64

internal object UnpaddedBase64 {
    private val impl = Base64.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    fun encode(data: ByteArray): String = impl.encode(data)

    fun decode(data: String): ByteArray = impl.decode(data)
}