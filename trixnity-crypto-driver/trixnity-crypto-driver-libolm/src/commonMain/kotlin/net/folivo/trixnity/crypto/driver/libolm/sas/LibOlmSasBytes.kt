package net.folivo.trixnity.crypto.driver.libolm.sas

import net.folivo.trixnity.crypto.driver.sas.SasBytes
import kotlin.jvm.JvmInline

@JvmInline
value class LibOlmSasBytes(private val inner: ByteArray) : SasBytes {

    override val bytes: ByteArray
        get() = inner

    override val decimals: SasBytes.Decimals
        get() {
            val bytes = inner.map(Byte::toUByte).map(UByte::toUShort)

            infix fun UShort.and(mask: Int): UShort = this and mask.toUShort()
            infix fun UShort.shl(bits: Int): UShort = (this.toUInt() shl bits).toUShort()
            infix fun UShort.shr(bits: Int): UShort = (this.toUInt() shr bits).toUShort()
            operator fun UShort.plus(value: Int): UShort = (this.toUInt() + value.toUInt()).toUShort()

            return SasBytes.Decimals(
                (((bytes[0] and 0xff) shl 5) or (bytes[1] shr 3)) + 1000,
                (((bytes[1] and 0x07) shl 10) or (bytes[2] shl 2) or (bytes[3] shr 6)) + 1000,
                (((bytes[3] and 0x3F) shl 7) or (bytes[4] shr 1)) + 1000,
            )
        }

    override val emojiIndices: SasBytes.Emojis
        get() {
            val bytes = inner.map(Byte::toUByte).map(UByte::toULong)

            val num = 0UL.plus(bytes[0] shl 40).plus(bytes[1] shl 32).plus(bytes[2] shl 24).plus(bytes[3] shl 16)
                .plus(bytes[4] shl 8).plus(bytes[5] shl 0)

            val mask = 63UL

            return SasBytes.Emojis(
                ((num shr 42) and mask).toUByte(),
                ((num shr 36) and mask).toUByte(),
                ((num shr 30) and mask).toUByte(),
                ((num shr 24) and mask).toUByte(),
                ((num shr 18) and mask).toUByte(),
                ((num shr 12) and mask).toUByte(),
                ((num shr 6) and mask).toUByte(),
            )
        }

    override fun close() {}
}