package net.folivo.trixnity.client.store.repository.realm

import com.benasher44.uuid.uuid4
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import net.folivo.trixnity.client.store.MediaCacheMapping
import java.io.File

class RealmMediaCacheMappingRepositoryTest : ShouldSpec({
    timeout = 10_000
    lateinit var realm: Realm
    lateinit var cut: RealmMediaCacheMappingRepository

    beforeTest {
        val realmDbPath = "build/test-db/${uuid4()}"
        realm = Realm.open(
            RealmConfiguration.Builder(
                schema = setOf(
                    RealmMediaCacheMapping::class,
                )
            ).apply { directory(realmDbPath) }.build()
        )

        cut = RealmMediaCacheMappingRepository()
    }
    should("save, get and delete") {
        val key1 = "uri1"
        val key2 = "uri2"
        val mediaCacheMapping1 = MediaCacheMapping(key1, "mxcUri1", 2, ContentType.Text.Plain.toString())
        val mediaCacheMapping2 = MediaCacheMapping(key2, null, 3, ContentType.Image.PNG.toString())
        val uploadMedia2Copy = mediaCacheMapping2.copy(mxcUri = "mxcUri2")

        writeTransaction(realm) {
            cut.save(key1, mediaCacheMapping1)
            cut.save(key2, mediaCacheMapping2)
            cut.get(key1) shouldBe mediaCacheMapping1
            cut.get(key2) shouldBe mediaCacheMapping2
            cut.save(key2, uploadMedia2Copy)
            cut.get(key2) shouldBe uploadMedia2Copy
            cut.delete(key1)
            cut.get(key1) shouldBe null
        }
    }
})