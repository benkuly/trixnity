package net.folivo.trixnity.client.store.transaction

import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun createTransactionModule() = module {
    singleOf(::TransactionManagerImpl) { bind<TransactionManager>() }
}