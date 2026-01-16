package de.connect2x.trixnity.crypto.driver.vodozemac

import de.connect2x.trixnity.crypto.driver.CryptoDriver
import de.connect2x.trixnity.crypto.driver.vodozemac.keys.VodozemacKeyFactory
import de.connect2x.trixnity.crypto.driver.vodozemac.megolm.VodozemacMegolmFactory
import de.connect2x.trixnity.crypto.driver.vodozemac.olm.VodozemacOlmFactory
import de.connect2x.trixnity.crypto.driver.vodozemac.pkencryption.VodozemacPkFactory
import de.connect2x.trixnity.crypto.driver.vodozemac.sas.VodozemacSasFactory

object VodozemacCryptoDriver : CryptoDriver {
    override val key: VodozemacKeyFactory = VodozemacKeyFactory

    override val olm: VodozemacOlmFactory = VodozemacOlmFactory
    override val megolm: VodozemacMegolmFactory = VodozemacMegolmFactory

    override val pk: VodozemacPkFactory = VodozemacPkFactory

    override val sas: VodozemacSasFactory = VodozemacSasFactory
}