package net.folivo.trixnity.client

import net.folivo.trixnity.client.crypto.createCryptoModule
import net.folivo.trixnity.client.key.IKeyService
import net.folivo.trixnity.client.key.createKeyModule
import net.folivo.trixnity.client.media.IMediaService
import net.folivo.trixnity.client.media.createMediaModule
import net.folivo.trixnity.client.push.IPushService
import net.folivo.trixnity.client.push.createPushModule
import net.folivo.trixnity.client.room.IRoomService
import net.folivo.trixnity.client.room.LastRelevantEventFilter
import net.folivo.trixnity.client.room.createRoomModule
import net.folivo.trixnity.client.room.outbox.defaultOutboxMessageMediaUploaderMappings
import net.folivo.trixnity.client.store.createStoreModule
import net.folivo.trixnity.client.user.IUserService
import net.folivo.trixnity.client.user.createUserModule
import net.folivo.trixnity.client.verification.IVerificationService
import net.folivo.trixnity.client.verification.createVerificationModule
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.events.DefaultEventContentSerializerMappings
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings
import org.koin.dsl.module

fun createDefaultEventContentSerializerMappingsModule() = module {
    single<EventContentSerializerMappings> { DefaultEventContentSerializerMappings }
}

fun createDefaultOutboxMessageMediaUploaderMappingsModule() = module {
    single { defaultOutboxMessageMediaUploaderMappings }
}

fun createDefaultMatrixJsonModule() = module {
    single { createMatrixEventJson(get()) }
}

fun createDefaultLastRelevantEventFilter() = module {
    single<LastRelevantEventFilter> { { it is Event.MessageEvent<*> } }
}

fun createDefaultModules() = listOf(
    createDefaultEventContentSerializerMappingsModule(),
    createDefaultOutboxMessageMediaUploaderMappingsModule(),
    createDefaultMatrixJsonModule(),
    createDefaultLastRelevantEventFilter(),
    createStoreModule(),
    createRoomModule(),
    createUserModule(),
    createKeyModule(),
    createCryptoModule(),
    createVerificationModule(),
    createMediaModule(),
    createPushModule(),
)

val IMatrixClient.room
    get() = di.get<IRoomService>()

val IMatrixClient.user
    get() = di.get<IUserService>()

val IMatrixClient.media
    get() = di.get<IMediaService>()

val IMatrixClient.verification
    get() = di.get<IVerificationService>()

val IMatrixClient.key
    get() = di.get<IKeyService>()

val IMatrixClient.push
    get() = di.get<IPushService>()