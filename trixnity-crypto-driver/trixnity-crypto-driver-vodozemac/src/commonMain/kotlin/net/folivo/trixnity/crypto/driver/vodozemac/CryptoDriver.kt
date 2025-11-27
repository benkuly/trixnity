package net.folivo.trixnity.crypto.driver.vodozemac

import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.crypto.driver.vodozemac.keys.VodozemacKeyFactory
import net.folivo.trixnity.crypto.driver.vodozemac.megolm.VodozemacMegolmFactory
import net.folivo.trixnity.crypto.driver.vodozemac.olm.VodozemacOlmFactory
import net.folivo.trixnity.crypto.driver.vodozemac.pkencryption.VodozemacPkFactory
import net.folivo.trixnity.crypto.driver.vodozemac.sas.VodozemacSasFactory

object VodozemacCryptoDriver : CryptoDriver {
    override val key: VodozemacKeyFactory = VodozemacKeyFactory

    override val olm: VodozemacOlmFactory = VodozemacOlmFactory
    override val megolm: VodozemacMegolmFactory = VodozemacMegolmFactory

    override val pk: VodozemacPkFactory = VodozemacPkFactory

    override val sas: VodozemacSasFactory = VodozemacSasFactory
}