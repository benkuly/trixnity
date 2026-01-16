package de.connect2x.trixnity.utils

import kotlin.random.Random

private val defaultAlphabet = ('a'..'z') + ('A'..'Z') + ('0'..'9')

/**
 * Returns a string using an alphabet between a-z, A-Z and 0-9.
 */
fun Random.nextString(length: Int, alphabet: List<Char> = defaultAlphabet): String =
    buildString(length) {
        repeat(length) {
            append(alphabet.random(this@nextString))
        }
    }