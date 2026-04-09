package de.connect2x.trixnity.client.room

import de.connect2x.trixnity.client.key.KeyBackupService
import de.connect2x.trixnity.client.key.OutgoingRoomKeyRequestEventHandler
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.MSC4354
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
            roomStore = get(),
            roomEventEncryptionServices = getAll(),
            userService = get(),
            mediaService = get(),
            roomOutboxMessageStore = get(),
            outboxMessageMediaUploaderMappings = get(),
            currentSyncState = get(),
            userInfo = get(),
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
    @OptIn(MSC4354::class)
    singleOf(::TimelineEventHandlerImpl) {
        bind<TimelineEventHandler>()
        bind<EventHandler>()
        named<TimelineEventHandlerImpl>()
    }
    @OptIn(MSC4354::class)
    single<EventHandler>(named<StickyEventHandler>()) {
        StickyEventHandler(
            api = get(),
            stickyEventStore = get(),
            roomEventEncryptionServices = getAll(),
            clock = get(),
            tm = get(),
            config = get(),
        )
    }
    @OptIn(MSC4354::class)
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
    @OptIn(MSC4354::class)
    single<RoomService> {
        RoomServiceImpl(
            api = get(),
            roomStore = get(),
            roomStateStore = get(),
            roomAccountDataStore = get(),
            roomTimelineStore = get(),
            stickyEventStore = get(),
            roomOutboxMessageStore = get(),
            roomEventEncryptionServices = getAll(),
            forgetRoomService = get(),
            mediaService = get(),
            userInfo = get(),
            timelineEventHandler = get(named<TimelineEventHandlerImpl>()),
            typingEventHandler = get(named<TypingEventHandlerImpl>()),
            clock = get(),
            currentSyncState = get(),
            scope = get(),
            matrixClientConfig = get(),
        )
    }
}
