package net.folivo.trixnity.crypto.driver.vodozemac.sas

import net.folivo.trixnity.crypto.driver.sas.SasBytes
import net.folivo.trixnity.vodozemac.sas.SasBytes as Inner
import kotlin.jvm.JvmInline

@JvmInline
value class VodozemacSasBytes(val inner: Inner) : SasBytes {

    override val bytes: ByteArray
        get() = inner.bytes

    override val decimals: SasBytes.Decimals
        get() = with(inner.decimals) {
            SasBytes.Decimals(
                first = first,
                second = second,
                third = third,
            )
        }

    override val emojiIndices: SasBytes.Emojis
        get() = with(inner.emojiIndices) {
            SasBytes.Emojis(
                emojiIndex1 = emojiIndex1,
                emojiIndex2 = emojiIndex2,
                emojiIndex3 = emojiIndex3,
                emojiIndex4 = emojiIndex4,
                emojiIndex5 = emojiIndex5,
                emojiIndex6 = emojiIndex6,
                emojiIndex7 = emojiIndex7,
            )
        }

    override fun close() = inner.close()
}