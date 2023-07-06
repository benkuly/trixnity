package net.folivo.trixnity.client.store.repository.test

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.*
import net.folivo.trixnity.client.store.MediaCacheMapping
import net.folivo.trixnity.client.store.repository.MediaCacheMappingRepository
import net.folivo.trixnity.client.store.transaction.RepositoryTransactionManager
import org.koin.core.Koin


fun ShouldSpec.mediaCacheMappingRepositoryTest(diReceiver: () -> Koin) {
    lateinit var cut: MediaCacheMappingRepository
    lateinit var rtm: RepositoryTransactionManager
    beforeTest {
        val di = diReceiver()
        cut = di.get()
        rtm = di.get()
    }
    should("mediaCacheMappingRepositoryTest: save, get and delete") {
        val key1 = "uri1"
        val key2 = "uri2"
        val mediaCacheMapping1 = MediaCacheMapping(key1, "mxcUri1", 2, ContentType.Text.Plain.toString())
        val mediaCacheMapping2 = MediaCacheMapping(key2, null, 3, ContentType.Image.PNG.toString())
        val uploadMedia2Copy = mediaCacheMapping2.copy(mxcUri = "mxcUri2")

        rtm.writeTransaction {
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
}