package net.folivo.trixnity.crypto.driver.libvodozemac.sas

import net.folivo.trixnity.crypto.driver.libvodozemac.keys.LibVodozemacCurve25519PublicKey
import net.folivo.trixnity.crypto.driver.sas.Mac
import net.folivo.trixnity.crypto.driver.sas.EstablishedSas
import kotlin.jvm.JvmInline
import net.folivo.trixnity.vodozemac.sas.EstablishedSas as Inner

@JvmInline
value class LibVodozemacEstablishedSas(
    val inner: Inner,
) : EstablishedSas {

    override val ourPublicKey: LibVodozemacCurve25519PublicKey
        get() = LibVodozemacCurve25519PublicKey(inner.ourPublicKey)

    override val theirPublicKey: LibVodozemacCurve25519PublicKey
        get() = LibVodozemacCurve25519PublicKey(inner.theirPublicKey)

    override fun generateBytes(info: String): LibVodozemacSasBytes
            = LibVodozemacSasBytes(inner.generateBytes(info))

    override fun calculateMac(input: String, info: String): LibVodozemacMac
            = LibVodozemacMac(inner.calculateMac(input, info))

    override fun calculateMacInvalidBase64(input: String, info: String): String
            = inner.calculateMacInvalidBase64(input, info)

    override fun verifyMac(input: String, info: String, tag: Mac) {
        require(tag is LibVodozemacMac)

        inner.verifyMac(input, info, tag.inner)
    }

    override fun close()
        = inner.close()
}