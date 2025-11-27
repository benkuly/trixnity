package net.folivo.trixnity.crypto.driver.libolm.pkencryption

import net.folivo.trixnity.crypto.driver.libolm.keys.LibOlmCurve25519PublicKey
import net.folivo.trixnity.crypto.driver.libolm.keys.LibOlmCurve25519SecretKey
import net.folivo.trixnity.crypto.driver.libolm.keys.LibOlmCurve25519SecretKeyFactory
import net.folivo.trixnity.crypto.driver.pkencryption.PkDecryption
import net.folivo.trixnity.crypto.driver.pkencryption.PkMessage
import net.folivo.trixnity.libolm.OlmPkDecryption
import kotlin.jvm.JvmInline

@JvmInline
value class LibOlmPkDecryption(private val inner: OlmPkDecryption) : PkDecryption {

    override val publicKey: LibOlmCurve25519PublicKey
        get() = LibOlmCurve25519PublicKey(inner.publicKey)

    override val secretKey: LibOlmCurve25519SecretKey
        get() = LibOlmCurve25519SecretKeyFactory(inner.privateKey)

    override fun decrypt(message: PkMessage): String {
        require(message is LibOlmPkMessage)

        return inner.decrypt(message.inner)
    }

    override fun close() = inner.free()
}