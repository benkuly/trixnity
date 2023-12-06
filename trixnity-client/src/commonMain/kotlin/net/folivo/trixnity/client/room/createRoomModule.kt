package net.folivo.trixnity.client.room

import net.folivo.trixnity.client.key.KeyBackupService
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
    single<EventHandler>(named<OutboxMessageEventHandler>()) {
        OutboxMessageEventHandler(
            config = get(),
            api = get(),
            roomEventEncryptionServices = getAll(),
            mediaService = get(),
            roomStore = get(),
            roomOutboxMessageStore = get(),
            outboxMessageMediaUploaderMappings = get(),
            currentSyncState = get(),
            tm = get()
        )
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
    single<RoomEventEncryptionService>(named<MegolmRoomEventEncryptionService>()) {
        MegolmRoomEventEncryptionService(
            roomStore = get(),
            userService = get(),
            roomStateStore = get(),
            olmCryptoStore = get(),
            keyBackupService = get(named<KeyBackupService>()),
            outgoingRoomKeyRequestEventHandler = get(named<OutgoingRoomKeyRequestEventHandler>()),
            olmEncryptionService = get(),
        )
    }
    singleOf(::UnencryptedRoomEventEncryptionService) {
        bind<RoomEventEncryptionService>()
        named<UnencryptedRoomEventEncryptionService>()
    }
    single<RoomService> {
        RoomServiceImpl(
            api = get(),
            roomStore = get(),
            roomUserStore = get(),
            roomStateStore = get(),
            roomAccountDataStore = get(),
            roomTimelineStore = get(),
            roomOutboxMessageStore = get(),
            roomEventEncryptionServices = getAll(),
            mediaService = get(),
            userInfo = get(),
            timelineEventHandler = get(named<TimelineEventHandlerImpl>()),
            typingEventHandler = get(named<TypingEventHandler>()),
            currentSyncState = get(),
            scope = get(),
            config = get(),
        )
    }
}