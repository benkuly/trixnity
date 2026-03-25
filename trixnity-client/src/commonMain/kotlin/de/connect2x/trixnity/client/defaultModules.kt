package de.connect2x.trixnity.client

import de.connect2x.trixnity.client.cryptodriver.createCryptoModule
import de.connect2x.trixnity.client.key.KeyBackupService
import de.connect2x.trixnity.client.key.KeyService
import de.connect2x.trixnity.client.key.OutgoingRoomKeyRequestEventHandler
import de.connect2x.trixnity.client.key.createKeyModule
import de.connect2x.trixnity.client.media.MediaService
import de.connect2x.trixnity.client.media.createMediaModule
import de.connect2x.trixnity.client.notification.NotificationService
import de.connect2x.trixnity.client.notification.createNotificationModule
import de.connect2x.trixnity.client.room.DirectRoomEventHandler
import de.connect2x.trixnity.client.room.ForgetRoomService
import de.connect2x.trixnity.client.room.ForgetRoomServiceImpl
import de.connect2x.trixnity.client.room.MegolmRoomEventEncryptionService
import de.connect2x.trixnity.client.room.OutboxMessageEventHandler
import de.connect2x.trixnity.client.room.RoomAccountDataEventHandler
import de.connect2x.trixnity.client.room.RoomEventEncryptionService
import de.connect2x.trixnity.client.room.RoomListHandler
import de.connect2x.trixnity.client.room.RoomService
import de.connect2x.trixnity.client.room.RoomServiceImpl
import de.connect2x.trixnity.client.room.RoomStateEventHandler
import de.connect2x.trixnity.client.room.RoomUpgradeHandler
import de.connect2x.trixnity.client.room.TimelineEventHandler
import de.connect2x.trixnity.client.room.TypingEventHandler
import de.connect2x.trixnity.client.room.UnencryptedRoomEventEncryptionService
import de.connect2x.trixnity.client.room.createRoomModule
import de.connect2x.trixnity.client.room.outbox.OutboxMessageMediaUploaderMappings
import de.connect2x.trixnity.client.room.outbox.default
import de.connect2x.trixnity.client.server.createServerModule
import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.createStoreModule
import de.connect2x.trixnity.client.user.CanDoAction
import de.connect2x.trixnity.client.user.CanDoActionImpl
import de.connect2x.trixnity.client.user.GetPowerLevel
import de.connect2x.trixnity.client.user.GetPowerLevelImpl
import de.connect2x.trixnity.client.user.GlobalAccountDataEventHandler
import de.connect2x.trixnity.client.user.LoadMembersService
import de.connect2x.trixnity.client.user.LoadMembersServiceImpl
import de.connect2x.trixnity.client.user.UserService
import de.connect2x.trixnity.client.user.UserServiceImpl
import de.connect2x.trixnity.client.user.createUserModule
import de.connect2x.trixnity.client.verification.VerificationService
import de.connect2x.trixnity.client.verification.createVerificationModule
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.clientserverapi.client.SyncApiClient
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.TypingEventContent
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.default
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.named
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Clock

fun createMatrixClientStartedModule() = module {
    single<MatrixClientStarted> { MatrixClientStarted() }
}

fun createClockModule() = module {
    single<Clock> { Clock.System }
}

fun createDefaultEventContentSerializerMappingsModule() = module {
    single<EventContentSerializerMappings> { EventContentSerializerMappings.default }
}

fun createDefaultOutboxMessageMediaUploaderMappingsModule() = module {
    single<OutboxMessageMediaUploaderMappings> { OutboxMessageMediaUploaderMappings.default }
}

fun createDefaultMatrixJsonModule() = module {
    single<Json> {
        val mappings = get<EventContentSerializerMappings>()
        val config = get<MatrixClientConfiguration>()
        createMatrixEventJson(mappings, customModule = SerializersModule {
            contextual(
                TimelineEvent.Serializer(
                    mappings.message + mappings.state,
                    config.storeTimelineEventContentUnencrypted
                )
            )
        })
    }
}

fun createDefaultMatrixClientAuthProviderSerializerMappings() = module {
    single<MatrixClientAuthProviderDataSerializerMappings> { MatrixClientAuthProviderDataSerializerMappings.default }
}

fun createCurrentSyncStateModule() = module {
    single<CurrentSyncState> { CurrentSyncState(get<MatrixClientServerApiClient>()) }
}

fun createTrixnityDefaultModuleFactories(): List<ModuleFactory> = listOf(
    ::createMatrixClientStartedModule,
    ::createClockModule,
    ::createServerModule,
    ::createDefaultEventContentSerializerMappingsModule,
    ::createDefaultOutboxMessageMediaUploaderMappingsModule,
    ::createDefaultMatrixJsonModule,
    ::createDefaultMatrixClientAuthProviderSerializerMappings,
    ::createCurrentSyncStateModule,
    ::createStoreModule,
    ::createRoomModule,
    ::createUserModule,
    ::createKeyModule,
    ::createCryptoModule,
    ::createVerificationModule,
    ::createMediaModule,
    ::createNotificationModule,
)

/**
 * Use this, if you want to create a bot with basic functionality. You don't have access to some data usually provided
 * by Trixnity (for example [RoomUser] or [TimelineEvent]).
 *
 * Instead, you need to manually listen to the sync events via [SyncApiClient] (can be received via [MatrixClient.api]).
 * You can encrypt and decrypt events by iterating through all [RoomEventEncryptionService] (can be received via [MatrixClient.roomEventEncryptionServices])
 * and use the first non-null result. For sending events asynchronously you can still use the outbox.
 *
 */
fun createTrixnityBotModuleFactories(): List<ModuleFactory> = listOf(
    ::createMatrixClientStartedModule,
    ::createClockModule,
    ::createServerModule,
    ::createDefaultEventContentSerializerMappingsModule,
    ::createDefaultOutboxMessageMediaUploaderMappingsModule,
    ::createDefaultMatrixJsonModule,
    ::createDefaultMatrixClientAuthProviderSerializerMappings,
    ::createCurrentSyncStateModule,
    ::createStoreModule,
    ::createKeyModule,
    ::createCryptoModule,
    ::createMediaModule,
    {
        module {
            singleOf(::RoomListHandler) {
                bind<EventHandler>()
                named<RoomListHandler>()
            }
            singleOf(::RoomStateEventHandler) {
                bind<EventHandler>()
                named<RoomStateEventHandler>()
            }
            singleOf(::RoomAccountDataEventHandler) {
                bind<EventHandler>()
                named<RoomAccountDataEventHandler>()
            }
            singleOf(::GlobalAccountDataEventHandler) {
                bind<EventHandler>()
                named<GlobalAccountDataEventHandler>()
            }
            singleOf(::DirectRoomEventHandler) {
                bind<EventHandler>()
                named<DirectRoomEventHandler>()
            }
            singleOf(::RoomUpgradeHandler) {
                bind<EventHandler>()
                named<RoomUpgradeHandler>()
            }
            singleOf(::ForgetRoomServiceImpl) {
                bind<ForgetRoomService>()
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
                    timelineEventHandler = object : TimelineEventHandler {
                        override suspend fun unsafeFillTimelineGaps(
                            startEventId: EventId,
                            roomId: RoomId,
                            limit: Long
                        ): Result<Unit> {
                            throw IllegalStateException("TimelineEvents are not supported in bot mode")
                        }
                    },
                    typingEventHandler = object : TypingEventHandler {
                        override val usersTyping: StateFlow<Map<RoomId, TypingEventContent>> = MutableStateFlow(mapOf())
                    },
                    clock = get(),
                    currentSyncState = get(),
                    scope = get(),
                    matrixClientConfig = get(),
                )
            }
            singleOf(::GetPowerLevelImpl) { bind<GetPowerLevel>() }
            singleOf(::CanDoActionImpl) { bind<CanDoAction>() }
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
    }
)

val MatrixClient.room
    get() = di.get<RoomService>()

val MatrixClient.user
    get() = di.get<UserService>()

val MatrixClient.media
    get() = di.get<MediaService>()

val MatrixClient.verification
    get() = di.get<VerificationService>()

val MatrixClient.key
    get() = di.get<KeyService>()

val MatrixClient.notification
    get() = di.get<NotificationService>()

val MatrixClient.roomEventEncryptionServices
    get() = di.getAll<RoomEventEncryptionService>()
