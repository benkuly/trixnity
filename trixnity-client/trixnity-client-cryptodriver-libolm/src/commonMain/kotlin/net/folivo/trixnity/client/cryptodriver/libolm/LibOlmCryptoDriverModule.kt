package net.folivo.trixnity.client.cryptodriver.libolm

import net.folivo.trixnity.client.CryptoDriverModule
import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.crypto.driver.libolm.LibOlmCryptoDriver
import org.koin.dsl.module

fun CryptoDriverModule.Companion.libOlm() = CryptoDriverModule {
    module {
        single<CryptoDriver> { LibOlmCryptoDriver }
    }
}