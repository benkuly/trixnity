package net.folivo.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.core.serialization.event.EventContentSerializerMappings

abstract class Store(
    scope: CoroutineScope,
    contentMappings: EventContentSerializerMappings,
    private val rtm: RepositoryTransactionManager,
    accountRepository: AccountRepository,
    outdatedKeysRepository: OutdatedKeysRepository,
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
) {
    val account = AccountStore(accountRepository, rtm, scope)
    val keys = KeysStore(
        outdatedKeysRepository,
        deviceKeysRepository,
        crossSigningKeysRepository,
        keyVerificationStateRepository,
        rtm,
        scope
    )
    val olm = OlmStore(
        olmAccountRepository,
        olmSessionRepository,
        inboundMegolmSessionRepository,
        inboundMegolmMessageIndexRepository,
        outboundMegolmSessionRepository,
        rtm,
        scope
    )
    val room = RoomStore(roomRepository, rtm, scope)
    val roomUser = RoomUserStore(roomUserRepository, rtm, scope)
    val roomState = RoomStateStore(roomStateRepository, rtm, contentMappings, scope)
    val roomTimeline = RoomTimelineStore(roomTimelineRepository, rtm, scope)
    val roomOutboxMessage = RoomOutboxMessageStore(roomOutboxMessageRepository, rtm, scope)
    val media = MediaStore(mediaRepository, uploadMediaRepository, rtm, scope)
    val globalAccountData = GlobalAccountDataStore(globalAccountDataRepository, rtm, contentMappings, scope)
    val roomAccountData = RoomAccountDataStore(roomAccountDataRepository, rtm, contentMappings, scope)

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
        return rtm.transaction {
            try {
                block()
            } catch (error: Throwable) {
                resetCache()
                throw error
            }
        }
    }
}