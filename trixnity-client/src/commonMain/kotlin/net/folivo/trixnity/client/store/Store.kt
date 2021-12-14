package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings

abstract class Store(
    scope: CoroutineScope,
    accountRepository: AccountRepository,
    outdatedDeviceKeysRepository: OutdatedDeviceKeysRepository,
    deviceKeysRepository: DeviceKeysRepository,
    crossSigningKeysRepository: CrossSigningKeysRepository,
    keyVerificationStateRepository: KeyVerificationStateRepository,
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
    globalAccountDataRepository: GlobalAccountDataRepository,
    roomAccountDataRepository: RoomAccountDataRepository,
    contentMappings: EventContentSerializerMappings,
) {
    val account = AccountStore(accountRepository, scope)
    val keys = KeysStore(
        outdatedDeviceKeysRepository,
        deviceKeysRepository,
        crossSigningKeysRepository,
        keyVerificationStateRepository,
        scope
    )
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
    val globalAccountData = GlobalAccountDataStore(globalAccountDataRepository, contentMappings, scope)
    val roomAccountData = RoomAccountDataStore(roomAccountDataRepository, contentMappings, scope)

    suspend fun init() {
        account.init()
        keys.init()
        olm.init()
        room.init()
        roomOutboxMessage.init()
    }

    private fun resetCache() {
        // at the moment only roomTimeline is used with transactions
        roomTimeline.resetCache()
    }

    suspend fun <T : Any> transaction(block: suspend () -> T): T {
        return databaseTransaction {
            try {
                block()
            } catch (error: Throwable) {
                resetCache()
                throw error
            }
        }
    }

    protected abstract suspend fun <T : Any> databaseTransaction(block: suspend () -> T): T
}