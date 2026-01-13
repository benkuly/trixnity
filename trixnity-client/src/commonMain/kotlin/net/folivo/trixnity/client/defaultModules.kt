package net.folivo.trixnity.client

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.folivo.trixnity.client.cryptodriver.createCryptoModule
import net.folivo.trixnity.client.key.KeyBackupService
import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.client.key.OutgoingRoomKeyRequestEventHandler
import net.folivo.trixnity.client.key.createKeyModule
import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.client.media.createMediaModule
import net.folivo.trixnity.client.notification.NotificationService
import net.folivo.trixnity.client.notification.createNotificationModule
import net.folivo.trixnity.client.room.*
import net.folivo.trixnity.client.room.outbox.OutboxMessageMediaUploaderMappings
import net.folivo.trixnity.client.room.outbox.defaultOutboxMessageMediaUploaderMappings
import net.folivo.trixnity.client.server.createServerModule
import net.folivo.trixnity.client.store.RoomUser
import net.folivo.trixnity.client.store.TimelineEvent
import net.folivo.trixnity.client.store.TimelineEventSerializer
import net.folivo.trixnity.client.store.createStoreModule
import net.folivo.trixnity.client.user.*
import net.folivo.trixnity.client.verification.VerificationService
import net.folivo.trixnity.client.verification.createVerificationModule
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.SyncApiClient
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.m.TypingEventContent
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
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
    single<EventContentSerializerMappings> { DefaultEventContentSerializerMappings }
}

fun createDefaultOutboxMessageMediaUploaderMappingsModule() = module {
    single<OutboxMessageMediaUploaderMappings> { defaultOutboxMessageMediaUploaderMappings }
}

fun createDefaultMatrixJsonModule() = module {
    single<Json> {
        val mappings = get<EventContentSerializerMappings>()
        val config = get<MatrixClientConfiguration>()
        createMatrixEventJson(mappings, customModule = SerializersModule {
            contextual(
                TimelineEventSerializer(
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
                    config = get(),
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