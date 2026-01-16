package de.connect2x.trixnity.crypto.driver.vodozemac.sas

import de.connect2x.trixnity.crypto.driver.keys.Curve25519PublicKey
import de.connect2x.trixnity.crypto.driver.vodozemac.keys.VodozemacCurve25519PublicKey
import de.connect2x.trixnity.crypto.driver.sas.Sas
import de.connect2x.trixnity.vodozemac.sas.Sas as VodozemacSas
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class VodozemacSas(val inner: VodozemacSas) : Sas {

    override val publicKey: VodozemacCurve25519PublicKey
        get() = VodozemacCurve25519PublicKey(inner.publicKey)

    override fun diffieHellman(theirPublicKey: Curve25519PublicKey): VodozemacEstablishedSas {
        require(theirPublicKey is VodozemacCurve25519PublicKey)

        return VodozemacEstablishedSas(inner.diffieHellman(theirPublicKey.inner))
    }

    override fun close() = inner.close()
}