package net.folivo.trixnity.client.room

import net.folivo.trixnity.client.key.KeyBackupServiceImpl
import net.folivo.trixnity.client.key.OutgoingRoomKeyRequestEventHandler
import net.folivo.trixnity.core.EventHandler
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.named
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun createRoomModule() = module {
    singleOf(::TimelineMutex)
    singleOf(::RoomListHandler) {
        bind<EventHandler>()
        named<RoomListHandler>()
    }
    singleOf(::DirectRoomEventHandler) {
        bind<EventHandler>()
        named<DirectRoomEventHandler>()
    }
    singleOf(::RoomEncryptionEventHandler) {
        bind<EventHandler>()
        named<RoomEncryptionEventHandler>()
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
    singleOf(::TypingEventHandler) {
        bind<EventHandler>()
        named<TypingEventHandler>()
    }
    singleOf(::RoomUpgradeHandler) {
        bind<EventHandler>()
        named<RoomUpgradeHandler>()
    }
    singleOf(::TimelineEventHandlerImpl) {
        bind<TimelineEventHandler>()
        bind<EventHandler>()
        named<TimelineEventHandlerImpl>()
    }
    single<RoomEventDecryptionService>(named<MegolmRoomEventDecryptionService>()) {
        MegolmRoomEventDecryptionService(
            get(),
            get(named<KeyBackupServiceImpl>()),
            get(named<OutgoingRoomKeyRequestEventHandler>()),
            get()
        )
    }
    single<RoomService> {
        RoomServiceImpl(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            getAll(),
            get(),
            get(named<TimelineEventHandlerImpl>()),
            get(named<TypingEventHandler>()),
            get(),
            get(),
            get(),
        )
    }
}