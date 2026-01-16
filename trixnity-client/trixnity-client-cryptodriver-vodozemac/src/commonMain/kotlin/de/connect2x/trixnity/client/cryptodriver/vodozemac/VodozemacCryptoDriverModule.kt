package de.connect2x.trixnity.client.cryptodriver.vodozemac

import de.connect2x.trixnity.client.CryptoDriverModule
import de.connect2x.trixnity.client.store.repository.RepositoryMigration
import de.connect2x.trixnity.crypto.driver.CryptoDriver
import de.connect2x.trixnity.crypto.driver.vodozemac.VodozemacCryptoDriver
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun CryptoDriverModule.Companion.vodozemac() = CryptoDriverModule {
    module {
        single<CryptoDriver> { VodozemacCryptoDriver }
        singleOf(::VodozemacRepositoryMigration) { bind<RepositoryMigration>() }
    }
}