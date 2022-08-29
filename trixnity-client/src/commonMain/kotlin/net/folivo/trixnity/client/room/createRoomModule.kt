package net.folivo.trixnity.client.room

import net.folivo.trixnity.client.key.KeyBackupService
import net.folivo.trixnity.core.EventHandler
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.named
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun createRoomModule() = module {
    singleOf(::TimelineMutex)
    singleOf(::DirectRoomEventHandler) {
        bind<EventHandler>()
        named<DirectRoomEventHandler>()
    }
    singleOf(::EncryptionEventHandler) {
        bind<EventHandler>()
        named<EncryptionEventHandler>()
    }
    singleOf(::MembershipEventHandler) {
        bind<EventHandler>()
        named<MembershipEventHandler>()
    }
    singleOf(::OutboxMessageEventHandler) {
        bind<EventHandler>()
        named<OutboxMessageEventHandler>()
    }
    singleOf(::RoomAccountDataEventHandler) {
        bind<EventHandler>()
        named<RoomAccountDataEventHandler>()
    }
    singleOf(::RoomAvatarUrlEventHandler) {
        bind<EventHandler>()
        named<RoomAvatarUrlEventHandler>()
    }
    singleOf(::RoomDisplayNameEventHandler) {
        bind<EventHandler>()
        named<RoomDisplayNameEventHandler>()
    }
    singleOf(::RoomStateEventHandler) {
        bind<EventHandler>()
        named<RoomStateEventHandler>()
    }
    singleOf(::TimelineEventHandler) {
        bind<ITimelineEventHandler>()
        bind<EventHandler>()
        named<TimelineEventHandler>()
    }
    single<RoomEventDecryptionService>(named<MegolmRoomEventDecryptionService>()) {
        MegolmRoomEventDecryptionService(get(), get(named<KeyBackupService>()), get())
    }
    single<IRoomService> {
        RoomService(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            getAll(),
            get(),
            get(named<TimelineEventHandler>()),
            get(),
            get(),
        )
    }
}