package net.folivo.trixnity.client.store.cache

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import net.folivo.trixnity.client.store.repository.MinimalStoreRepository
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class StateFlowCacheTest : ShouldSpec({
    val repository = mockk<MinimalStoreRepository<String, String>>(relaxUnitFun = true)
    lateinit var cacheScope: CoroutineScope
    lateinit var cut: StateFlowCache<String, String, MinimalStoreRepository<String, String>>

    beforeTest {
        cacheScope = CoroutineScope(Dispatchers.Default)
    }
    afterTest {
        clearAllMocks()
        cacheScope.cancel()
        cut = mockk() // just in case we forgot to init a new cut for a test
    }

    context("readWithCache") {
        should("read value from repository and update cache") {
            cut = StateFlowCache(cacheScope, repository)
            cut.readWithCache(
                key = "key",
                containsInCache = { false },
                retrieveFromRepoAndUpdateCache = { cacheValue, repository ->
                    cacheValue shouldBe null
                    repository shouldBe repository
                    "a new value"
                }
            ).value shouldBe "a new value"
            // value is now in cache, but we say it isn't
            cut.readWithCache(
                key = "key",
                containsInCache = { false },
                retrieveFromRepoAndUpdateCache = { cacheValue, _ ->
                    cacheValue shouldBe "a new value"
                    "another value"
                }
            ).value shouldBe "another value"
        }
        should("not read value from repository") {
            cut = StateFlowCache(cacheScope, repository)
            // we say, the value is in cache, but actually it is not, so the cache asks for it
            cut.readWithCache(
                key = "key",
                containsInCache = { true },
                retrieveFromRepoAndUpdateCache = { _, _ ->
                    "a new value"
                }
            ).value shouldBe "a new value"
            // now there is a value in cache and the cache does not ask for it
            var wasCalled = false
            cut.readWithCache(
                key = "key",
                containsInCache = { true },
                retrieveFromRepoAndUpdateCache = { _, _ ->
                    wasCalled = true
                    "another value"
                }
            ).value shouldBe "a new value"
            wasCalled shouldBe false
        }
        should("prefer cache") {
            cut = StateFlowCache(cacheScope, repository)
            cut.readWithCache(
                key = "key",
                containsInCache = { false },
                retrieveFromRepoAndUpdateCache = { cacheValue, repository ->
                    cacheValue shouldBe null
                    repository shouldBe repository
                    "a new value"
                }
            ).value shouldBe "a new value"
            cut.readWithCache(
                key = "key",
                containsInCache = { true },
                retrieveFromRepoAndUpdateCache = { _, _ ->
                    "another value"
                }
            ).value shouldBe "a new value"
        }
        context("with coroutine scope") {
            should("remove from cache") {
                cut = StateFlowCache(cacheScope, repository)
                val readScope1 = CoroutineScope(Dispatchers.Default)
                val readScope2 = CoroutineScope(Dispatchers.Default)
                cut.readWithCache(
                    key = "key",
                    containsInCache = { true },
                    retrieveFromRepoAndUpdateCache = { _, _ ->
                        "a new value"
                    },
                    readScope1
                ).value shouldBe "a new value"
                readScope1.cancel()
                cut.readWithCache(
                    key = "key",
                    containsInCache = { true },
                    retrieveFromRepoAndUpdateCache = { _, _ ->
                        // if the key is in cache, this will never be called
                        "another value"
                    },
                    readScope2
                ).value shouldBe "another value"
                // calling it without scope should run a remover job and therefore cancelling a scope should not remove value from cache
                cut.readWithCache(
                    key = "key",
                    containsInCache = { true },
                    retrieveFromRepoAndUpdateCache = { _, _ ->
                        // if the key is in cache, this will never be called
                        "yet another value"
                    }
                ).value shouldBe "another value"
                readScope2.cancel()
                cut.readWithCache(
                    key = "key",
                    containsInCache = { true },
                    retrieveFromRepoAndUpdateCache = { _, _ ->
                        // if the key is in cache, this will never be called
                        "yet another value"
                    }
                ).value shouldBe "another value"
            }
        }
        context("without coroutine scope") {
            should("remove from cache, when read cache time expired") {
                cut = StateFlowCache(cacheScope, repository, readCacheTime = milliseconds(30))
                cut.readWithCache(
                    key = "key",
                    containsInCache = { true },
                    retrieveFromRepoAndUpdateCache = { _, _ ->
                        "a new value"
                    }
                ).value shouldBe "a new value"
                delay(40)
                cut.readWithCache(
                    key = "key",
                    containsInCache = { true },
                    retrieveFromRepoAndUpdateCache = { _, _ ->
                        "another value"
                    }
                ).value shouldBe "another value"
                // we check, that the value is not removed before the time expires
                val readScope = CoroutineScope(Dispatchers.Default)
                cut.readWithCache(
                    key = "key",
                    containsInCache = { true },
                    retrieveFromRepoAndUpdateCache = { _, _ ->
                        "yet another value"
                    },
                    readScope
                ).value shouldBe "another value"
                // and that the value is not removed from cache, when there is a scope, that uses it
                delay(40)
                cut.readWithCache(
                    key = "key",
                    containsInCache = { true },
                    retrieveFromRepoAndUpdateCache = { _, _ ->
                        "yet another value"
                    },
                    readScope
                ).value shouldBe "another value"
                readScope.cancel()
            }
        }
        context("infinite cache enabled") {
            should("never remove from cache") {
                cut = StateFlowCache(cacheScope, repository, true, readCacheTime = milliseconds(10))
                val readScope = CoroutineScope(Dispatchers.Default)
                cut.readWithCache(
                    key = "key",
                    containsInCache = { true },
                    retrieveFromRepoAndUpdateCache = { _, _ ->
                        "a new value"
                    },
                    readScope
                ).value shouldBe "a new value"
                readScope.cancel()
                cut.readWithCache(
                    key = "key",
                    containsInCache = { true },
                    retrieveFromRepoAndUpdateCache = { _, _ ->
                        "another value"
                    }
                ).value shouldBe "a new value"
                delay(50)
                cut.readWithCache(
                    key = "key",
                    containsInCache = { true },
                    retrieveFromRepoAndUpdateCache = { _, _ ->
                        "another value"
                    }
                ).value shouldBe "a new value"
            }
        }
    }
    context("get") {
        beforeTest { cut = StateFlowCache(cacheScope, repository) }
        should("read from database") {
            coEvery { repository.get("key") } returns "value"
            cut.get("key").value shouldBe "value"
        }
        should("prefer cache") {
            coEvery { repository.get("key") } returns "value" andThen "value2"
            cut.get("key").value shouldBe "value"
            cut.get("key").value shouldBe "value"
        }
    }
    context("writeWithCache") {
        should("read value from repository and update cache") {
            cut = StateFlowCache(cacheScope, repository)
            cut.writeWithCache(
                key = "key",
                updater = { oldValue ->
                    oldValue shouldBe "from db"
                    "updated value"
                },
                containsInCache = { false },
                getFromRepositoryAndUpdateCache = { cacheValue, repository ->
                    cacheValue shouldBe null
                    repository shouldBe repository
                    "from db"
                },
                persistIntoRepository = { newValue, repository ->
                    newValue shouldBe "updated value"
                    repository shouldBe repository
                }
            )
            cut.writeWithCache(
                key = "key",
                updater = { oldValue ->
                    oldValue shouldBe "from db 2"
                    "updated value 2"
                },
                containsInCache = { false },
                getFromRepositoryAndUpdateCache = { cacheValue, repository ->
                    cacheValue shouldBe "updated value"
                    repository shouldBe repository
                    "from db 2"
                },
                persistIntoRepository = { newValue, repository ->
                    newValue shouldBe "updated value 2"
                    repository shouldBe repository
                }
            )
        }
        should("prefer cache") {
            cut = StateFlowCache(cacheScope, repository)
            cut.writeWithCache(
                key = "key",
                updater = { "updated value" },
                containsInCache = { false },
                getFromRepositoryAndUpdateCache = { _, _ -> null },
                persistIntoRepository = { _, _ -> }
            )
            var wasCalled = false
            cut.writeWithCache(
                key = "key",
                updater = { oldValue ->
                    oldValue shouldBe "updated value"
                    "updated value 2"
                },
                containsInCache = { true },
                getFromRepositoryAndUpdateCache = { _, _ ->
                    wasCalled = true
                    "from db 2"
                },
                persistIntoRepository = { _, _ -> }
            )
            wasCalled shouldBe false
        }
        should("not save unchanged value") {
            cut = StateFlowCache(cacheScope, repository)
            cut.writeWithCache(
                key = "key",
                updater = { "updated value" },
                containsInCache = { false },
                getFromRepositoryAndUpdateCache = { _, _ -> null },
                persistIntoRepository = { _, _ -> }
            )
            var wasCalled = false
            cut.writeWithCache(
                key = "key",
                updater = { "updated value" },
                containsInCache = { true },
                getFromRepositoryAndUpdateCache = { _, _ -> null },
                persistIntoRepository = { _, _ -> wasCalled = true }
            )
            wasCalled shouldBe false
        }
        context("infinite cache not enabled") {
            should("remove from cache, when write cache time expired") {
                cut = StateFlowCache(cacheScope, repository, writeCacheTime = milliseconds(30))
                cut.writeWithCache(
                    key = "key",
                    updater = { "updated value" },
                    containsInCache = { false },
                    getFromRepositoryAndUpdateCache = { _, _ -> null },
                    persistIntoRepository = { _, _ -> }
                )
                delay(30)
                var wasCalled = false
                cut.writeWithCache(
                    key = "key",
                    updater = { "updated value" },
                    containsInCache = { true },
                    getFromRepositoryAndUpdateCache = { _, _ ->
                        wasCalled = true
                        null
                    },
                    persistIntoRepository = { _, _ -> }
                )
                wasCalled shouldBe true
            }
        }
        context("infinite cache enabled") {
            should("never remove from cache") {
                cut = StateFlowCache(cacheScope, repository, infiniteCache = true, writeCacheTime = milliseconds(10))
                cut.writeWithCache(
                    key = "key",
                    updater = { "updated value" },
                    containsInCache = { false },
                    getFromRepositoryAndUpdateCache = { _, _ -> null },
                    persistIntoRepository = { _, _ -> }
                )
                delay(30)
                var wasCalled = false
                cut.writeWithCache(
                    key = "key",
                    updater = { oldValue ->
                        oldValue shouldBe "updated value"
                        "updated value"
                    },
                    containsInCache = { true },
                    getFromRepositoryAndUpdateCache = { _, _ ->
                        wasCalled = true
                        null
                    },
                    persistIntoRepository = { _, _ -> }
                )
                wasCalled shouldBe false
            }
        }
    }
    context("update") {
        beforeTest { cut = StateFlowCache(cacheScope, repository) }
        should("read from database") {
            coEvery { repository.get("key") } returns "old"
            cut.update("key") {
                it shouldBe "old"
                "value"
            }
        }
        should("prefer cache") {
            coEvery { repository.get("key") } returns "old" andThen "dino"
            cut.update("key") {
                it shouldBe "old"
                "value"
            }
            cut.update("key") {
                it shouldBe "value"
                "new value"
            }
        }
        should("save to database") {
            coEvery { repository.get("key") } returns "old"
            cut.update("key") { "value" }
            coVerify { repository.save("key", "value") }
        }
        should("remove from database") {
            coEvery { repository.get("key") } returns "old"
            cut.update("key") { null }
            coVerify { repository.delete("key") }
        }
    }
})