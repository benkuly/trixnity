package net.folivo.trixnity.client.store.repository.room

import androidx.room.TypeConverter
import kotlinx.datetime.Instant
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.RelationType
import net.folivo.trixnity.core.model.keys.KeyAlgorithm

internal object EventIdConverter {
    @TypeConverter
    fun from(string: String?): EventId? = string?.let(::EventId)

    @TypeConverter
    fun to(id: EventId?): String? = id?.full
}

internal object InstantConverter {
    @TypeConverter
    fun from(timeMs: Long?): Instant? = timeMs?.let(Instant::fromEpochMilliseconds)

    @TypeConverter
    fun to(instant: Instant?): Long? = instant?.toEpochMilliseconds()
}

internal object KeyAlgorithmConverter {
    @TypeConverter
    fun from(string: String?): KeyAlgorithm? = string?.let(KeyAlgorithm::of)

    @TypeConverter
    fun to(alg: KeyAlgorithm?): String? = alg?.name
}

internal object RelationTypeConverter {
    @TypeConverter
    fun from(string: String?): RelationType? = string?.let(RelationType::of)

    @TypeConverter
    fun to(id: RelationType?): String? = id?.name
}

internal object RoomIdConverter {
    @TypeConverter
    fun from(string: String?): RoomId? = string?.let(::RoomId)

    @TypeConverter
    fun to(id: RoomId?): String? = id?.full
}

internal object UserIdConverter {
    @TypeConverter
    fun from(string: String?): UserId? = string?.let(::UserId)

    @TypeConverter
    fun to(id: UserId?): String? = id?.full
}