package net.folivo.trixnity.client.store.repository.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        RoomAccount::class,
        RoomCrossSigningKeys::class,
        RoomDeviceKeys::class,
        RoomGlobalAccountData::class,
        RoomInboundMegolmMessageIndex::class,
        RoomInboundMegolmSession::class,
        RoomKeyChainLink::class,
        RoomKeyVerificationState::class,
        RoomMediaCacheMapping::class,
        RoomOlmAccount::class,
        RoomOlmForgetFallbackKeyAfter::class,
        RoomOlmSession::class,
        RoomOutboundMegolmSession::class,
        RoomOutdatedKeys::class,
        RoomRoomAccountData::class,
        RoomRoomKeyRequest::class,
        RoomRoomOutboxMessage::class,
        RoomRoom::class,
        RoomRoomState::class,
        RoomRoomUser::class,
        RoomSecretKeyRequest::class,
        RoomSecrets::class,
        RoomTimelineEventRelation::class,
        RoomTimelineEvent::class,
    ],
    version = 1, // tick this value when any entity classes change
    exportSchema = true,
)
@TypeConverters(
    EventIdConverter::class,
    InstantConverter::class,
    KeyAlgorithmConverter::class,
    RelationTypeConverter::class,
    RoomIdConverter::class,
    UserIdConverter::class,
)
internal abstract class TrixnityRoomDatabase : RoomDatabase() {
    abstract fun account(): AccountDao
    abstract fun crossSigningKeys(): CrossSigningKeysDao
    abstract fun deviceKeys(): DeviceKeysDao
    abstract fun globalAccountData(): GlobalAccountDataDao
    abstract fun inboundMegolmMessageIndex(): InboundMegolmMessageIndexDao
    abstract fun inboundMegolmSession(): InboundMegolmSessionDao
    abstract fun keyChainLink(): KeyChainLinkDao
    abstract fun keyVerificationState(): KeyVerificationStateDao
    abstract fun mediaCacheMapping(): MediaCacheMappingDao
    abstract fun olmAccount(): OlmAccountDao
    abstract fun olmForgetFallbackKeyAfter(): OlmForgetFallbackKeyAfterDao
    abstract fun olmSession(): OlmSessionDao
    abstract fun outboundMegolmSession(): OutboundMegolmSessionDao
    abstract fun outdatedKeys(): OutdatedKeysDao
    abstract fun roomAccountData(): RoomAccountDataDao
    abstract fun roomKeyRequest(): RoomKeyRequestDao
    abstract fun roomOutboxMessage(): RoomOutboxMessageDao
    abstract fun room(): RoomRoomDao
    abstract fun roomState(): RoomStateDao
    abstract fun roomUser(): RoomUserDao
    abstract fun secretKeyRequest(): SecretKeyRequestDao
    abstract fun secrets(): SecretsDao
    abstract fun timelineEventRelation(): TimelineEventRelationDao
    abstract fun timelineEvent(): TimelineEventDao
}
