package net.folivo.trixnity.client.cryptodriver.vodozemac

import net.folivo.trixnity.client.CryptoDriverModule
import net.folivo.trixnity.client.store.repository.RepositoryMigration
import net.folivo.trixnity.crypto.driver.CryptoDriver
import net.folivo.trixnity.crypto.driver.vodozemac.VodozemacCryptoDriver
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun CryptoDriverModule.Companion.vodozemac() = CryptoDriverModule {
    module {
        single<CryptoDriver> { VodozemacCryptoDriver }
        singleOf(::VodozemacRepositoryMigration) { bind<RepositoryMigration>() }
    }
}