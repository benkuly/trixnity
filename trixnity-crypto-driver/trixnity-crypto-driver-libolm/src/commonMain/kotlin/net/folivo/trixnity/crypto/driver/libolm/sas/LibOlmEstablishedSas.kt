package net.folivo.trixnity.crypto.driver.libolm.sas

import net.folivo.trixnity.crypto.driver.CryptoDriverException
import net.folivo.trixnity.crypto.driver.libolm.keys.LibOlmCurve25519PublicKey
import net.folivo.trixnity.crypto.driver.sas.EstablishedSas
import net.folivo.trixnity.crypto.driver.sas.Mac
import net.folivo.trixnity.libolm.OlmLibraryException
import net.folivo.trixnity.libolm.OlmSAS

class LibOlmEstablishedSas(
    private val inner: OlmSAS,
    override val theirPublicKey: LibOlmCurve25519PublicKey,
) : EstablishedSas {

    init {
        inner.setTheirPublicKey(theirPublicKey.inner)
    }

    override val ourPublicKey: LibOlmCurve25519PublicKey
        get() = LibOlmCurve25519PublicKey(inner.publicKey)

    override fun generateBytes(info: String): LibOlmSasBytes = LibOlmSasBytes(
        inner.generateShortCode(info, 6)
    )

    override fun calculateMac(input: String, info: String): LibOlmMac = LibOlmMac(
        inner.calculateMacFixedBase64(
            input, info
        )
    )

    override fun calculateMacInvalidBase64(input: String, info: String): String = inner.calculateMac(input, info)

    override fun verifyMac(input: String, info: String, tag: Mac) {
        require(tag is LibOlmMac)

        val calculated = calculateMac(input, info)

        if (calculated.base64 != tag.base64) throw CryptoDriverException(OlmLibraryException("mismatched MAC tag"))
    }

    override fun close() = inner.free()
}