package net.folivo.trixnity.utils

import kotlin.random.Random

private val defaultAlphabet = ('a'..'z') + ('A'..'Z')

/**
 * Returns a string between a-Z.
 */
fun Random.nextString(length: Int, alphabet: List<Char> = defaultAlphabet): String =
    buildString(length) {
        repeat(length) {
            append(alphabet.random(this@nextString))
        }
    }