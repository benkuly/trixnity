package net.folivo.trixnity.crypto.driver.libvodozemac.sas

import net.folivo.trixnity.crypto.driver.keys.Curve25519PublicKey
import net.folivo.trixnity.crypto.driver.libvodozemac.keys.LibVodozemacCurve25519PublicKey
import net.folivo.trixnity.crypto.driver.sas.Sas
import net.folivo.trixnity.vodozemac.sas.Sas as LibVodozemacSas
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class LibVodozemacSas(val inner: LibVodozemacSas) : Sas {

    override val publicKey: LibVodozemacCurve25519PublicKey
        get() = LibVodozemacCurve25519PublicKey(inner.publicKey)

    override fun diffieHellman(theirPublicKey: Curve25519PublicKey): LibVodozemacEstablishedSas {
        require(theirPublicKey is LibVodozemacCurve25519PublicKey)

        return LibVodozemacEstablishedSas(inner.diffieHellman(theirPublicKey.inner))
    }

    override fun close()
        = inner.close()
}