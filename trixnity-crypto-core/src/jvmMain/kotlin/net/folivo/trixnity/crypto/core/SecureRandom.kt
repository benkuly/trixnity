package net.folivo.trixnity.crypto.core

private val secureRandom by lazy { java.security.SecureRandom() }

actual fun fillRandomBytes(array: ByteArray) = secureRandom.nextBytes(array)