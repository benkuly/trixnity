package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Room
import androidx.room.RoomDatabase
import de.connect2x.trixnity.utils.nextString
import kotlin.random.Random

actual fun randomDatabaseBuilder(): RoomDatabase.Builder<TrixnityRoomDatabase> =
    Room.databaseBuilder<TrixnityRoomDatabase>("${Random.nextString(12)}.db")