package net.folivo.trixnity.utils

import kotlin.random.Random

private val alphabet = ('a'..'z') + ('A'..'Z')

/**
 * Returns a string between a-Z.
 */
fun Random.nextString(length: Int): String =
    buildString(length) {
        repeat(length) { append(alphabet.random()) }
    }