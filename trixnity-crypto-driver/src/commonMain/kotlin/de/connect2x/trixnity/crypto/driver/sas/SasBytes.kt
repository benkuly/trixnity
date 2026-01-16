package de.connect2x.trixnity.crypto.driver.sas

interface SasBytes : AutoCloseable {
    val emojiIndices: Emojis
    val decimals: Decimals
    val bytes: ByteArray

    data class Emojis(
        val emojiIndex1: UByte,
        val emojiIndex2: UByte,
        val emojiIndex3: UByte,
        val emojiIndex4: UByte,
        val emojiIndex5: UByte,
        val emojiIndex6: UByte,
        val emojiIndex7: UByte,
    ) {
        val asList: List<Int> = listOf(
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
        val asList: List<Int> = listOf(
            first.toInt(),
            second.toInt(),
            third.toInt(),
        )
    }
}