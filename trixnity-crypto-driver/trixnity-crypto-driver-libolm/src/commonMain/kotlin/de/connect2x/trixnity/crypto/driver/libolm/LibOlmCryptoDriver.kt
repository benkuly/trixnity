package de.connect2x.trixnity.crypto.driver.libolm

import de.connect2x.trixnity.crypto.driver.CryptoDriver
import de.connect2x.trixnity.crypto.driver.libolm.keys.LibOlmKeyFactories
import de.connect2x.trixnity.crypto.driver.libolm.megolm.LibOlmMegolmFactory
import de.connect2x.trixnity.crypto.driver.libolm.olm.LibOlmOlmFactory
import de.connect2x.trixnity.crypto.driver.libolm.pkencryption.LibOlmPkFactory
import de.connect2x.trixnity.crypto.driver.libolm.sas.LibOlmSasFactory

object LibOlmCryptoDriver : CryptoDriver {
    override val key: LibOlmKeyFactories = LibOlmKeyFactories

    override val olm: LibOlmOlmFactory = LibOlmOlmFactory
    override val megolm: LibOlmMegolmFactory = LibOlmMegolmFactory
    override val pk: LibOlmPkFactory = LibOlmPkFactory

    override val sas: LibOlmSasFactory = LibOlmSasFactory
}