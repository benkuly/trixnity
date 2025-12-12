package net.folivo.trixnity.client.store.repository.room

import androidx.room.Room
import androidx.room.RoomDatabase
import net.folivo.trixnity.utils.nextString
import kotlin.random.Random

actual fun inMemoryDatabaseBuilder(): RoomDatabase.Builder<TrixnityRoomDatabase> =
    Room.databaseBuilder<TrixnityRoomDatabase>("build/tmp/test/${Random.nextString(12)}.db")