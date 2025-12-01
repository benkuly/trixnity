package net.folivo.trixnity.client.store.repository.room

import androidx.room.*

@Database(
    entities = [
        RoomAccount::class,
        RoomAuthentication::class,
        RoomServerData::class,
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
        RoomUserPresence::class,
        RoomRoomUserReceipts::class,
        RoomSecretKeyRequest::class,
        RoomSecrets::class,
        RoomTimelineEventRelation::class,
        RoomTimelineEvent::class,
        RoomNotification::class,
        RoomNotificationState::class,
        RoomNotificationUpdate::class,
        RoomMigration::class,
    ],
    version = 6, // tick this value when any entity classes change
    autoMigrations = [
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
    ],
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

@ConstructedBy(TrixnityRoomDatabaseConstructor::class)
abstract class TrixnityRoomDatabase : RoomDatabase() {
    abstract fun account(): AccountDao
    abstract fun authentication(): AuthenticationDao
    abstract fun serverData(): ServerDataDao
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
    abstract fun roomUserReceipts(): RoomUserReceiptsDao
    abstract fun secretKeyRequest(): SecretKeyRequestDao
    abstract fun secrets(): SecretsDao
    abstract fun timelineEventRelation(): TimelineEventRelationDao
    abstract fun timelineEvent(): TimelineEventDao
    abstract fun userPresence(): UserPresenceDao
    abstract fun notification(): NotificationDao
    abstract fun notificationState(): NotificationStateDao
    abstract fun notificationUpdate(): NotificationUpdateDao
    abstract fun migration(): MigrationDao
}

// The Room compiler generates the `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object TrixnityRoomDatabaseConstructor : RoomDatabaseConstructor<TrixnityRoomDatabase> {
    override fun initialize(): TrixnityRoomDatabase
}