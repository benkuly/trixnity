package net.folivo.trixnity.client.user

import net.folivo.trixnity.core.EventHandler
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.named
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun createUserModule() = module {
    singleOf(::UserMemberEventHandler) {
        bind<EventHandler>()
        named<UserMemberEventHandler>()
    }
    singleOf(::PresenceEventHandler) {
        bind<EventHandler>()
        named<PresenceEventHandler>()
    }
    singleOf(::ReceiptEventHandler) {
        bind<EventHandler>()
        named<ReceiptEventHandler>()
    }
    singleOf(::GlobalAccountDataEventHandler) {
        bind<EventHandler>()
        named<GlobalAccountDataEventHandler>()
    }
    single<UserService> {
        UserServiceImpl(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(named<PresenceEventHandler>()),
            get(),
            get(),
            get(),
            get(),
        )
    }
}