package net.folivo.trixnity.crypto.driver

import net.folivo.trixnity.crypto.driver.keys.KeyFactories
import net.folivo.trixnity.crypto.driver.megolm.MegolmFactory
import net.folivo.trixnity.crypto.driver.olm.OlmFactory
import net.folivo.trixnity.crypto.driver.pkencryption.PkFactory
import net.folivo.trixnity.crypto.driver.sas.SasFactory

interface CryptoDriver {
    val key: KeyFactories
    val olm: OlmFactory
    val megolm: MegolmFactory
    val pk: PkFactory
    val sas: SasFactory
}

