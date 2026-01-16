package de.connect2x.trixnity.crypto.driver

import de.connect2x.trixnity.crypto.driver.keys.KeyFactories
import de.connect2x.trixnity.crypto.driver.megolm.MegolmFactory
import de.connect2x.trixnity.crypto.driver.olm.OlmFactory
import de.connect2x.trixnity.crypto.driver.pkencryption.PkFactory
import de.connect2x.trixnity.crypto.driver.sas.SasFactory

interface CryptoDriver {
    val key: KeyFactories
    val olm: OlmFactory
    val megolm: MegolmFactory
    val pk: PkFactory
    val sas: SasFactory
}

