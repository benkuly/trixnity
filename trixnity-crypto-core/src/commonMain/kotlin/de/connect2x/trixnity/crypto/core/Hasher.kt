package de.connect2x.trixnity.crypto.core

interface Hasher : AutoCloseable {
    fun update(input: ByteArray)
    fun digest(): ByteArray
    override fun close()
}