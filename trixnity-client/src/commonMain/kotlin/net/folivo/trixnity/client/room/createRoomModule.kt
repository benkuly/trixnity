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
            roomOutboxMessageStore = get(),
            outboxMessageMediaUploaderMappings = get(),
            currentSyncState = get(),
            tm = get(),
            clock = get(),
        )
    }
    singleOf(::RoomAccountDataEventHandler) {
        bind<EventHandler>()
        named<RoomAccountDataEventHandler>()
    }
    singleOf(::RoomStateEventHandler) {
        bind<EventHandler>()
        named<RoomStateEventHandler>()
    }
    singleOf(::TypingEventHandlerImpl) {
        bind<TypingEventHandler>()
        bind<EventHandler>()
        named<TypingEventHandlerImpl>()
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
    singleOf(::ForgetRoomServiceImpl) {
        bind<ForgetRoomService>()
    }
    single<RoomEventEncryptionService>(named<MegolmRoomEventEncryptionService>()) {
        MegolmRoomEventEncryptionService(
            roomStore = get(),
            loadMembersService = get(),
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
            roomStateStore = get(),
            roomAccountDataStore = get(),
            roomTimelineStore = get(),
            roomOutboxMessageStore = get(),
            roomEventEncryptionServices = getAll(),
            forgetRoomService = get(),
            mediaService = get(),
            userInfo = get(),
            timelineEventHandler = get(named<TimelineEventHandlerImpl>()),
            typingEventHandler = get(named<TypingEventHandlerImpl>()),
            currentSyncState = get(),
            scope = get(),
            config = get(),
        )
    }
}