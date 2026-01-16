package de.connect2x.trixnity.crypto.core

import kotlin.random.Random

object SecureRandom : Random() {
    override fun nextBytes(array: ByteArray, fromIndex: Int, toIndex: Int): ByteArray {
        val random = ByteArray(toIndex - fromIndex)
        fillRandomBytes(random)
        random.copyInto(array, fromIndex, 0, random.size)
        return array
    }

    override fun nextBits(bitCount: Int): Int {
        val numBytes = (bitCount + 7) / 8
        val b = nextBytes(numBytes)

        var next = 0
        for (i in 0 until numBytes) {
            next = (next shl 8) + (b[i].toInt() and 0xFF)
        }
        return next ushr numBytes * 8 - bitCount
    }
}

expect fun fillRandomBytes(array: ByteArray)