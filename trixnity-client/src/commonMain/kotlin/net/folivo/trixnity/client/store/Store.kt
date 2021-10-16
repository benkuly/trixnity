package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings

class Store(
    scope: CoroutineScope,
    accountRepository: AccountRepository,
    outdatedDeviceKeysRepository: OutdatedDeviceKeysRepository,
    deviceKeysRepository: DeviceKeysRepository,
    olmAccountRepository: OlmAccountRepository,
    olmSessionRepository: OlmSessionRepository,
    inboundMegolmSessionRepository: InboundMegolmSessionRepository,
    inboundMegolmMessageIndexRepository: InboundMegolmMessageIndexRepository,
    outboundMegolmSessionRepository: OutboundMegolmSessionRepository,
    roomRepository: RoomRepository,
    roomUserRepository: RoomUserRepository,
    roomStateRepository: RoomStateRepository,
    roomTimelineRepository: RoomTimelineRepository,
    roomOutboxMessageRepository: RoomOutboxMessageRepository,
    mediaRepository: MediaRepository,
    uploadMediaRepository: UploadMediaRepository,
    roomAccountDataRepository: RoomAccountDataRepository,
    contentMappings: EventContentSerializerMappings,
) {
    val account = AccountStore(accountRepository, scope)
    val deviceKeys = DeviceKeysStore(outdatedDeviceKeysRepository, deviceKeysRepository, scope)
    val olm = OlmStore(
        olmAccountRepository,
        olmSessionRepository,
        inboundMegolmSessionRepository,
        inboundMegolmMessageIndexRepository,
        outboundMegolmSessionRepository,
        scope
    )
    val room = RoomStore(roomRepository, scope)
    val roomUser = RoomUserStore(roomUserRepository, scope)
    val roomState = RoomStateStore(roomStateRepository, contentMappings, scope)
    val roomTimeline = RoomTimelineStore(roomTimelineRepository, scope)
    val roomOutboxMessage = RoomOutboxMessageStore(roomOutboxMessageRepository, scope)
    val media = MediaStore(mediaRepository, uploadMediaRepository, scope)
    val roomAccountData = RoomAccountDataStore(roomAccountDataRepository, contentMappings, scope)
    // TODO add accountData

    suspend fun init() {
        account.init()
        deviceKeys.init()
        olm.init()
        room.init()
        roomOutboxMessage.init()
    }
}