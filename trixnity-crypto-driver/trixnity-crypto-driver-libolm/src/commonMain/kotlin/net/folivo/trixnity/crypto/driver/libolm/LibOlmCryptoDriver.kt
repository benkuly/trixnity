package net.folivo.trixnity.crypto.driver.libolm

import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.crypto.driver.libolm.keys.LibOlmKeyFactories
import net.folivo.trixnity.crypto.driver.libolm.megolm.LibOlmMegolmFactory
import net.folivo.trixnity.crypto.driver.libolm.olm.LibOlmOlmFactory
import net.folivo.trixnity.crypto.driver.libolm.pkencryption.LibOlmPkFactory
import net.folivo.trixnity.crypto.driver.libolm.sas.LibOlmSasFactory

object LibOlmCryptoDriver : CryptoDriver {
    override val key: LibOlmKeyFactories = LibOlmKeyFactories

    override val olm: LibOlmOlmFactory = LibOlmOlmFactory
    override val megolm: LibOlmMegolmFactory = LibOlmMegolmFactory
    override val pk: LibOlmPkFactory = LibOlmPkFactory

    override val sas: LibOlmSasFactory = LibOlmSasFactory
}