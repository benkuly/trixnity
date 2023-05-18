package net.folivo.trixnity.client.store.repository.test

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import net.folivo.trixnity.client.store.repository.room.TrixnityRoomDatabase

private typealias DbBuilder = RoomDatabase.Builder<TrixnityRoomDatabase>

internal fun buildTestDatabase(
    extraConfig: DbBuilder.() -> DbBuilder = { this },
): TrixnityRoomDatabase {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return Room.inMemoryDatabaseBuilder(context, TrixnityRoomDatabase::class.java)
        .allowMainThreadQueries()
        .extraConfig()
        .build()
}
