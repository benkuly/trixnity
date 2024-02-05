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
        bind<LazyMemberEventHandler>()
        named<UserMemberEventHandler>()
    }
    singleOf(::PresenceEventHandlerImpl) {
        bind<PresenceEventHandler>()
        bind<EventHandler>()
        named<PresenceEventHandlerImpl>()
    }
    singleOf(::ReceiptEventHandler) {
        bind<EventHandler>()
        named<ReceiptEventHandler>()
    }
    singleOf(::GlobalAccountDataEventHandler) {
        bind<EventHandler>()
        named<GlobalAccountDataEventHandler>()
    }
    single<LoadMembersService> {
        LoadMembersServiceImpl(
            roomStore = get(),
            lazyMemberEventHandlers = getAll(),
            currentSyncState = get(),
            api = get(),
            scope = get(),
        )
    }
    single<UserService> {
        UserServiceImpl(
            roomUserStore = get(),
            roomStateStore = get(),
            roomTimelineStore = get(),
            globalAccountDataStore = get(),
            loadMembersService = get(),
            presenceEventHandler = get(named<PresenceEventHandlerImpl>()),
            userInfo = get(),
            mappings = get(),
        )
    }
}