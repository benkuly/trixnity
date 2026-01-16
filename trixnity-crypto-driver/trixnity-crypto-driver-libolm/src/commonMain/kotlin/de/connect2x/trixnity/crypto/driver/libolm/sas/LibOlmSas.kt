package de.connect2x.trixnity.crypto.driver.libolm.sas

import de.connect2x.trixnity.crypto.driver.keys.Curve25519PublicKey
import de.connect2x.trixnity.crypto.driver.libolm.keys.LibOlmCurve25519PublicKey
import de.connect2x.trixnity.crypto.driver.sas.Sas
import de.connect2x.trixnity.libolm.OlmSAS
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class LibOlmSas(private val inner: OlmSAS) : Sas {

    private var used = AtomicBoolean(false)

    override val publicKey: LibOlmCurve25519PublicKey
        get() = LibOlmCurve25519PublicKey(inner.publicKey)

    override fun diffieHellman(theirPublicKey: Curve25519PublicKey): LibOlmEstablishedSas {
        require(theirPublicKey is LibOlmCurve25519PublicKey)
        require(!used.exchange(true))

        return LibOlmEstablishedSas(
            inner, theirPublicKey
        )
    }

    override fun close() {
        if (used.load()) return

        inner.free()
    }
}