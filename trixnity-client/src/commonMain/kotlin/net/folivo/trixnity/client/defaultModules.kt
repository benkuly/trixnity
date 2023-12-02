package net.folivo.trixnity.client

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import net.folivo.trixnity.client.crypto.createCryptoModule
import net.folivo.trixnity.client.key.KeyService
import net.folivo.trixnity.client.key.createKeyModule
import net.folivo.trixnity.client.media.MediaService
import net.folivo.trixnity.client.media.createMediaModule
import net.folivo.trixnity.client.notification.NotificationService
import net.folivo.trixnity.client.notification.createNotificationModule
import net.folivo.trixnity.client.room.RoomService
import net.folivo.trixnity.client.room.createRoomModule
import net.folivo.trixnity.client.room.outbox.OutboxMessageMediaUploaderMappings
import net.folivo.trixnity.client.room.outbox.defaultOutboxMessageMediaUploaderMappings
import net.folivo.trixnity.client.store.TimelineEventSerializer
import net.folivo.trixnity.client.store.createStoreModule
import net.folivo.trixnity.client.user.UserService
import net.folivo.trixnity.client.user.createUserModule
import net.folivo.trixnity.client.verification.VerificationService
import net.folivo.trixnity.client.verification.createVerificationModule
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
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

fun createDefaultModules() = listOf(
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