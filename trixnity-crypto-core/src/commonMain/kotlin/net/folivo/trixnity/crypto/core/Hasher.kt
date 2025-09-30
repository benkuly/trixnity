package net.folivo.trixnity.crypto.core

interface Hasher : AutoCloseable {
    fun update(input: ByteArray)
    fun digest(): ByteArray
    override fun close()
}