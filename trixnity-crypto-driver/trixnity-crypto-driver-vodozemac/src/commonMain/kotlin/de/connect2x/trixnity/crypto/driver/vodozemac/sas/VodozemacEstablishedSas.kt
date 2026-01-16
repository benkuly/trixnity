package de.connect2x.trixnity.crypto.driver.vodozemac.sas

import de.connect2x.trixnity.crypto.driver.vodozemac.keys.VodozemacCurve25519PublicKey
import de.connect2x.trixnity.crypto.driver.sas.Mac
import de.connect2x.trixnity.crypto.driver.sas.EstablishedSas
import kotlin.jvm.JvmInline
import de.connect2x.trixnity.vodozemac.sas.EstablishedSas as Inner

@JvmInline
value class VodozemacEstablishedSas(
    val inner: Inner,
) : EstablishedSas {

    override val ourPublicKey: VodozemacCurve25519PublicKey
        get() = VodozemacCurve25519PublicKey(inner.ourPublicKey)

    override val theirPublicKey: VodozemacCurve25519PublicKey
        get() = VodozemacCurve25519PublicKey(inner.theirPublicKey)

    override fun generateBytes(info: String): VodozemacSasBytes = VodozemacSasBytes(inner.generateBytes(info))

    override fun calculateMac(input: String, info: String): VodozemacMac =
        VodozemacMac(inner.calculateMac(input, info))

    override fun calculateMacInvalidBase64(input: String, info: String): String =
        inner.calculateMacInvalidBase64(input, info)

    override fun verifyMac(input: String, info: String, tag: Mac) {
        require(tag is VodozemacMac)

        inner.verifyMac(input, info, tag.inner)
    }

    override fun close() = inner.close()
}