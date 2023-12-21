package net.folivo.trixnity.client

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.folivo.trixnity.client.crypto.createCryptoModule
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
import net.folivo.trixnity.client.store.TimelineEventSerializer
import net.folivo.trixnity.client.store.createStoreModule
import net.folivo.trixnity.client.user.LoadMembersService
import net.folivo.trixnity.client.user.LoadMembersServiceImpl
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.client.user.createUserModule
import net.folivo.trixnity.client.verification.VerificationService
import net.folivo.trixnity.client.verification.createVerificationModule
import net.folivo.trixnity.clientserverapi.client.SyncApiClient
import net.folivo.trixnity.core.EventHandler
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.named
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

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

fun createDefaultTrixnityModules() = listOf(
    createDefaultEventContentSerializerMappingsModule(),
    createDefaultOutboxMessageMediaUploaderMappingsModule(),
    createDefaultMatrixJsonModule(),
    createStoreModule(),
    createRoomModule(),
    createUserModule(),
    createKeyModule(),
    createCryptoModule(),
    createVerificationModule(),
    createMediaModule(),
    createNotificationModule(),
)

@Deprecated("use createDefaultTrixnityModules instead", ReplaceWith("createDefaultTrixnityModules()"))
fun createDefaultModules() = createDefaultTrixnityModules()

/**
 * Use this module, if you want to create a bot with basic functionality. You don't have access to usual services like
 * [RoomService] or [UserService].
 *
 * Instead, you need to manually listen to the sync events via [SyncApiClient] (can be received via [MatrixClient.api]).
 * You can encrypt and decrypt events by iterating through all [RoomEventEncryptionService] (can be received via [MatrixClient.roomEventEncryptionServices])
 * and use the first non-null result.
 *
 */
fun createTrixnityBotModules() = listOf(
    createDefaultEventContentSerializerMappingsModule(),
    createDefaultOutboxMessageMediaUploaderMappingsModule(),
    createDefaultMatrixJsonModule(),
    createStoreModule(),
    createKeyModule(),
    createCryptoModule(),
    module {
        singleOf(::RoomListHandler) {
            bind<EventHandler>()
            named<RoomListHandler>()
        }
        singleOf(::RoomStateEventHandler) {
            bind<EventHandler>()
            named<RoomStateEventHandler>()
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