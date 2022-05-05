package net.folivo.trixnity.client.key

/*
 * This file is based on an implementation from https://github.com/bitcoinj/bitcoinj/
 * and has been slightly modified.
 *
 * Licenced under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// TODO use a library for that!

const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

private val BASE58_REVERSE_ALPHABET by lazy {
    IntArray(128) { BASE58_ALPHABET.indexOf(it.toChar()) }
}

private const val BASE58_ENCODED_ZERO = '1'

internal fun ByteArray.encodeBase58(): String {
    if (this.isEmpty()) return ""
    val input = copyOf(size)
    val zeros = input.indexOfFirst { it.toInt() != 0 }.let { if (it < 0) input.size else it }
    val encoded = CharArray(input.size * 2)
    var outputStart = encoded.size
    var inputStart = zeros
    while (inputStart < input.size) {
        encoded[--outputStart] = BASE58_ALPHABET[divmod(input, inputStart.toUInt(), 256u, 58u).toInt()]
        if (input[inputStart].toInt() == 0) {
            ++inputStart
        }
    }

    while (outputStart < encoded.size && encoded[outputStart] == BASE58_ENCODED_ZERO) {
        ++outputStart
    }
    repeat(zeros) {
        encoded[--outputStart] = BASE58_ENCODED_ZERO
    }
    return encoded.concatToString(outputStart)
}

internal fun String.decodeBase58(): ByteArray {
    if (isEmpty()) ByteArray(0)
    val input58 = this.mapIndexed { index: Int, c: Char ->
        val digit = if (c.code < 128) BASE58_REVERSE_ALPHABET[c.code] else -1
        if (digit < 0) throw IllegalArgumentException("Illegal character $c at position $index")
        digit.toByte()
    }.toByteArray()

    val zeros = input58.indexOfFirst { it.toInt() != 0 }.let { if (it < 0) input58.size else it }
    val decoded = ByteArray(length)
    var outputStart = decoded.size
    var inputStart = zeros
    while (inputStart < input58.size) {
        decoded[--outputStart] = divmod(input58, inputStart.toUInt(), 58u, 256u).toByte()
        if (input58[inputStart].toInt() == 0) {
            ++inputStart
        }
    }
    while (outputStart < decoded.size && decoded[outputStart].toInt() == 0) {
        ++outputStart
    }
    return decoded.copyOfRange(outputStart - zeros, decoded.size)
}

private fun divmod(number: ByteArray, firstDigit: UInt, base: UInt, divisor: UInt): UInt {
    var remainder = 0u
    for (i in firstDigit until number.size.toUInt()) {
        val digit = number[i.toInt()].toUByte()
        val temp = remainder * base + digit
        number[i.toInt()] = (temp / divisor).toByte()
        remainder = temp % divisor
    }
    return remainder
}