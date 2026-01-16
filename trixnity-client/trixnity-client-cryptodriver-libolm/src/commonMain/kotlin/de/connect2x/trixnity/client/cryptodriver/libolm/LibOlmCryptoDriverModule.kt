package de.connect2x.trixnity.client.cryptodriver.libolm

import de.connect2x.trixnity.client.CryptoDriverModule
import de.connect2x.trixnity.crypto.driver.CryptoDriver
import de.connect2x.trixnity.crypto.driver.libolm.LibOlmCryptoDriver
import org.koin.dsl.module

fun CryptoDriverModule.Companion.libOlm() = CryptoDriverModule {
    module {
        single<CryptoDriver> { LibOlmCryptoDriver }
    }
}