package net.folivo.trixnity.crypto.driver.libvodozemac

import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.crypto.driver.libvodozemac.keys.LibVodozemacKeyFactory
import net.folivo.trixnity.crypto.driver.libvodozemac.megolm.LibVodozemacMegolmFactory
import net.folivo.trixnity.crypto.driver.libvodozemac.olm.LibVodozemacOlmFactory
import net.folivo.trixnity.crypto.driver.libvodozemac.pkencryption.LibVodozemacPkFactory
import net.folivo.trixnity.crypto.driver.libvodozemac.sas.LibVodozemacSasFactory

object LibVodozemacCryptoDriver : CryptoDriver {
    override val key: LibVodozemacKeyFactory = LibVodozemacKeyFactory

    override val olm: LibVodozemacOlmFactory = LibVodozemacOlmFactory
    override val megolm: LibVodozemacMegolmFactory = LibVodozemacMegolmFactory

    override val pk: LibVodozemacPkFactory = LibVodozemacPkFactory

    override val sas: LibVodozemacSasFactory = LibVodozemacSasFactory
}