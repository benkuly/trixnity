package de.connect2x.trixnity.client.user

import de.connect2x.trixnity.core.EventHandler
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.named
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun createUserModule() = module {
    singleOf(::GetPowerLevelImpl) { bind<GetPowerLevel>() }
    singleOf(::CanDoActionImpl) { bind<CanDoAction>() }
    singleOf(::UserMemberEventHandler) {
        bind<EventHandler>()
        bind<LazyMemberEventHandler>()
        named<UserMemberEventHandler>()
    }
    singleOf(::UserPresenceEventHandler) {
        bind<EventHandler>()
        named<UserPresenceEventHandler>()
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
            roomStore = get(),
            roomUserStore = get(),
            roomStateStore = get(),
            roomTimelineStore = get(),
            globalAccountDataStore = get(),
            userPresenceStore = get(),
            loadMembersService = get(),
            userInfo = get(),
            mappings = get(),
            currentSyncState = get(),
            canDoAction = get(),
            getPowerLevelDelegate = get(),
            clock = get(),
            config = get(),
        )
    }
}