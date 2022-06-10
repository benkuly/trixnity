package net.folivo.trixnity.client.store

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.core.serialization.events.EventContentSerializerMappings

abstract class Store(
    private val scope: CoroutineScope,
    contentMappings: EventContentSerializerMappings,
    private val rtm: RepositoryTransactionManager,
    accountRepository: AccountRepository,
    outdatedKeysRepository: OutdatedKeysRepository,
    deviceKeysRepository: DeviceKeysRepository,
    crossSigningKeysRepository: CrossSigningKeysRepository,
    keyVerificationStateRepository: KeyVerificationStateRepository,
    keyChainLinkRepository: KeyChainLinkRepository,
    secretsRepository: SecretsRepository,
    secretKeyRequestRepository: SecretKeyRequestRepository,
    olmAccountRepository: OlmAccountRepository,
    olmSessionRepository: OlmSessionRepository,
    inboundMegolmSessionRepository: InboundMegolmSessionRepository,
    inboundMegolmMessageIndexRepository: InboundMegolmMessageIndexRepository,
    outboundMegolmSessionRepository: OutboundMegolmSessionRepository,
    roomRepository: RoomRepository,
    roomUserRepository: RoomUserRepository,
    roomStateRepository: RoomStateRepository,
    roomTimelineEventRepository: RoomTimelineEventRepository,
    roomOutboxMessageRepository: RoomOutboxMessageRepository,
    mediaRepository: MediaRepository,
    uploadMediaRepository: UploadMediaRepository,
    globalAccountDataRepository: GlobalAccountDataRepository,
    roomAccountDataRepository: RoomAccountDataRepository,
) {
    val account = AccountStore(accountRepository, rtm, scope)
    val keys = KeyStore(
        outdatedKeysRepository,
        deviceKeysRepository,
        crossSigningKeysRepository,
        keyVerificationStateRepository,
        keyChainLinkRepository,
        secretsRepository,
        secretKeyRequestRepository,
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
    val roomTimeline = RoomTimelineStore(roomTimelineEventRepository, rtm, scope)
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

    private val deleteNonLocalMutex = Mutex()
    suspend fun deleteNonLocal() {
        deleteNonLocalMutex.withLock {
            keys.deleteNonLocal()
            room.deleteAll()
            roomUser.deleteAll()
            roomState.deleteAll()
            roomTimeline.deleteAll()
            roomOutboxMessage.deleteAll()
            media.deleteAll()
            globalAccountData.deleteAll()
            roomAccountData.deleteAll()
        }
    }

    private val deleteAllMutex = Mutex()
    suspend fun deleteAll() {
        deleteAllMutex.withLock {
            account.deleteAll()
            keys.deleteAll()
            olm.deleteAll()
            room.deleteAll()
            roomUser.deleteAll()
            roomState.deleteAll()
            roomTimeline.deleteAll()
            roomOutboxMessage.deleteAll()
            media.deleteAll()
            globalAccountData.deleteAll()
            roomAccountData.deleteAll()
        }
    }

    suspend fun <T : Any> transaction(block: suspend () -> T): T {
        return rtm.transaction {
            try {
                block()
            } catch (error: Throwable) {
                if (error !is CancellationException)
                    scope.cancel(CancellationException("transaction failed", error))
                throw error
            }
        }
    }
}