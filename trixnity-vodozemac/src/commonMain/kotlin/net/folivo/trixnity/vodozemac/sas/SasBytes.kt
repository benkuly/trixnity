package net.folivo.trixnity.vodozemac.sas

import net.folivo.trixnity.vodozemac.bindings.sas.SasBytesBindings
import net.folivo.trixnity.vodozemac.utils.Managed
import net.folivo.trixnity.vodozemac.utils.NativePointer
import net.folivo.trixnity.vodozemac.utils.managedReachableScope
import net.folivo.trixnity.vodozemac.utils.withResult

class SasBytes internal constructor(ptr: NativePointer) : Managed(ptr, SasBytesBindings::free) {

    val emojiIndices: Emojis
        get() = managedReachableScope {
            val result = withResult(ByteArray(7)) { SasBytesBindings.emojiIndices(ptr, it) }
            Emojis(
                result[0].toUByte(),
                result[1].toUByte(),
                result[2].toUByte(),
                result[3].toUByte(),
                result[4].toUByte(),
                result[5].toUByte(),
                result[6].toUByte(),
            )
        }

    val decimals: Decimals
        get() = managedReachableScope {
            val result = withResult(ShortArray(3)) { SasBytesBindings.decimals(ptr, it) }
            Decimals(result[0].toUShort(), result[1].toUShort(), result[2].toUShort())
        }

    val bytes: ByteArray
        get() = managedReachableScope {
            withResult(ByteArray(6)) { SasBytesBindings.asBytes(ptr, it) }
        }

    data class Emojis(
        val emojiIndex1: UByte,
        val emojiIndex2: UByte,
        val emojiIndex3: UByte,
        val emojiIndex4: UByte,
        val emojiIndex5: UByte,
        val emojiIndex6: UByte,
        val emojiIndex7: UByte,
    ) {
        val asList: List<Int> =
            listOf(
                emojiIndex1.toInt(),
                emojiIndex2.toInt(),
                emojiIndex3.toInt(),
                emojiIndex4.toInt(),
                emojiIndex5.toInt(),
                emojiIndex6.toInt(),
                emojiIndex7.toInt(),
            )
    }

    data class Decimals(
        val first: UShort,
        val second: UShort,
        val third: UShort,
    ) {
        val asList: List<Int> =
            listOf(
                first.toInt(),
                second.toInt(),
                third.toInt(),
            )
    }
}
