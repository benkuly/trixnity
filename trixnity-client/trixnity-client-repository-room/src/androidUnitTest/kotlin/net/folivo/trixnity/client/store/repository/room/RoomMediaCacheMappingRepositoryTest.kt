package net.folivo.trixnity.client.store.repository.room

import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.client.store.MediaCacheMapping
import net.folivo.trixnity.client.store.repository.test.buildTestDatabase
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomMediaCacheMappingRepositoryTest {
    private lateinit var db: TrixnityRoomDatabase
    private lateinit var repo: RoomMediaCacheMappingRepository

    @Before
    fun before() {
        db = buildTestDatabase()
        repo = RoomMediaCacheMappingRepository(db)
    }

    @Test
    fun `Save, get and delete`() = runTest {
        val key1 = "uri1"
        val key2 = "uri2"
        val mediaCacheMapping1 =
            MediaCacheMapping(key1, "mxcUri1", 2, ContentType.Text.Plain.toString())
        val mediaCacheMapping2 = MediaCacheMapping(key2, null, 3, ContentType.Image.PNG.toString())
        val uploadMedia2Copy = mediaCacheMapping2.copy(mxcUri = "mxcUri2")

        repo.save(key1, mediaCacheMapping1)
        repo.save(key2, mediaCacheMapping2)
        repo.get(key1) shouldBe mediaCacheMapping1
        repo.get(key2) shouldBe mediaCacheMapping2
        repo.save(key2, uploadMedia2Copy)
        repo.get(key2) shouldBe uploadMedia2Copy
        repo.delete(key1)
        repo.get(key1) shouldBe null
    }
}
