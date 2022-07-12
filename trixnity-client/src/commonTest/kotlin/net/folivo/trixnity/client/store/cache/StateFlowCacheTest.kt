package net.folivo.trixnity.client.store.cache

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.milliseconds

class StateFlowCacheTest : ShouldSpec({
    timeout = 60_000
    lateinit var cacheScope: CoroutineScope
    lateinit var cut: StateFlowCache<String, String>

    beforeTest {
        cacheScope = CoroutineScope(Dispatchers.Default)
    }
    afterTest {
        cacheScope.cancel()
    }

    context("readWithCache") {
        should("read value from repository and update cache") {
            cut = StateFlowCache(cacheScope)
            cut.readWithCache(
                key = "key",
                isContainedInCache = { false },
                retrieveAndUpdateCache = { cacheValue ->
                    cacheValue shouldBe null
                    "a new value"
                }
            ).value shouldBe "a new value"
            // value is now in cache, but we say it isn't
            cut.readWithCache(
                key = "key",
                isContainedInCache = { false },
                retrieveAndUpdateCache = { cacheValue ->
                    cacheValue shouldBe "a new value"
                    "another value"
                }
            ).value shouldBe "another value"
        }
        should("not read value from repository") {
            cut = StateFlowCache(cacheScope)
            // we say, the value is in cache, but actually it is not, so the cache asks for it
            cut.readWithCache(
                key = "key",
                isContainedInCache = { true },
                retrieveAndUpdateCache = {
                    "a new value"
                }
            ).value shouldBe "a new value"
            // now there is a value in cache and the cache does not ask for it
            var wasCalled = false
            cut.readWithCache(
                key = "key",
                isContainedInCache = { true },
                retrieveAndUpdateCache = {
                    wasCalled = true
                    "another value"
                }
            ).value shouldBe "a new value"
            wasCalled shouldBe false
        }
        should("prefer cache") {
            cut = StateFlowCache(cacheScope)
            cut.readWithCache(
                key = "key",
                isContainedInCache = { false },
                retrieveAndUpdateCache = { cacheValue ->
                    cacheValue shouldBe null
                    "a new value"
                }
            ).value shouldBe "a new value"
            cut.readWithCache(
                key = "key",
                isContainedInCache = { true },
                retrieveAndUpdateCache = {
                    "another value"
                }
            ).value shouldBe "a new value"
        }
        context("with coroutine scope") {
            should("remove from cache") {
                cut = StateFlowCache(cacheScope, cacheDuration = 50.milliseconds)
                val cache = cut.cache.stateIn(cacheScope)
                val readScope1 = CoroutineScope(Dispatchers.Default)
                val readScope2 = CoroutineScope(Dispatchers.Default)
                cut.readWithCache(
                    key = "key",
                    isContainedInCache = { true },
                    retrieveAndUpdateCache = {
                        "a new value"
                    },
                    readScope1
                ).value shouldBe "a new value"
                cache.first { it.isNotEmpty() }
                readScope1.cancel()
                cache.first { it.isEmpty() }
                cut.readWithCache(
                    key = "key",
                    isContainedInCache = { true },
                    retrieveAndUpdateCache = {
                        // if the key would be in cache, this would never be called
                        "another value"
                    },
                    readScope2
                ).value shouldBe "another value"
                // calling it without scope should run a remover job and therefore cancelling a scope should not remove value from cache
                cut.readWithCache(
                    key = "key",
                    isContainedInCache = { true },
                    retrieveAndUpdateCache = {
                        // if the key is in cache, this will never be called
                        "yet another value"
                    }
                ).value shouldBe "another value"
                readScope2.cancel()
                cut.readWithCache(
                    key = "key",
                    isContainedInCache = { true },
                    retrieveAndUpdateCache = {
                        // if the key is in cache, this will never be called
                        "yet another value"
                    }
                ).value shouldBe "another value"
            }
        }
        context("without coroutine scope") {
            should("remove from cache, when cache time expired") {
                cut = StateFlowCache(cacheScope, cacheDuration = 30.milliseconds)
                cut.readWithCache(
                    key = "key",
                    isContainedInCache = { true },
                    retrieveAndUpdateCache = {
                        "a new value"
                    }
                ).value shouldBe "a new value"
                delay(40)
                cut.readWithCache(
                    key = "key",
                    isContainedInCache = { true },
                    retrieveAndUpdateCache = {
                        "another value"
                    }
                ).value shouldBe "another value"
                // we check, that the value is not removed before the time expires
                val readScope = CoroutineScope(Dispatchers.Default)
                cut.readWithCache(
                    key = "key",
                    isContainedInCache = { true },
                    retrieveAndUpdateCache = {
                        "yet another value"
                    },
                    readScope
                ).value shouldBe "another value"
                // and that the value is not removed from cache, when there is a scope, that uses it
                delay(40)
                cut.readWithCache(
                    key = "key",
                    isContainedInCache = { true },
                    retrieveAndUpdateCache = {
                        "yet another value"
                    },
                    readScope
                ).value shouldBe "another value"
                readScope.cancel()
            }
        }
        context("infinite cache enabled") {
            should("never remove from cache") {
                cut = StateFlowCache(cacheScope, true, cacheDuration = 10.milliseconds)
                val readScope = CoroutineScope(Dispatchers.Default)
                cut.readWithCache(
                    key = "key",
                    isContainedInCache = { true },
                    retrieveAndUpdateCache = {
                        "a new value"
                    },
                    readScope
                ).value shouldBe "a new value"
                readScope.cancel()
                cut.readWithCache(
                    key = "key",
                    isContainedInCache = { true },
                    retrieveAndUpdateCache = {
                        "another value"
                    }
                ).value shouldBe "a new value"
                delay(50)
                cut.readWithCache(
                    key = "key",
                    isContainedInCache = { true },
                    retrieveAndUpdateCache = {
                        "another value"
                    }
                ).value shouldBe "a new value"
            }
        }
    }
    context("writeWithCache") {
        should("read value from repository and update cache") {
            cut = StateFlowCache(cacheScope)
            cut.writeWithCache(
                key = "key",
                updater = { oldValue ->
                    oldValue shouldBe "from db"
                    "updated value"
                },
                isContainedInCache = { false },
                retrieveAndUpdateCache = { cacheValue ->
                    cacheValue shouldBe null
                    "from db"
                },
                persist = { newValue ->
                    newValue shouldBe "updated value"
                }
            )
            cut.writeWithCache(
                key = "key",
                updater = { oldValue ->
                    oldValue shouldBe "from db 2"
                    "updated value 2"
                },
                isContainedInCache = { false },
                retrieveAndUpdateCache = { cacheValue ->
                    cacheValue shouldBe "updated value"
                    "from db 2"
                },
                persist = { newValue ->
                    newValue shouldBe "updated value 2"
                }
            )
        }
        should("prefer cache") {
            cut = StateFlowCache(cacheScope)
            cut.writeWithCache(
                key = "key",
                updater = { "updated value" },
                isContainedInCache = { false },
                retrieveAndUpdateCache = { null },
                persist = { }
            )
            var wasCalled = false
            cut.writeWithCache(
                key = "key",
                updater = { oldValue ->
                    oldValue shouldBe "updated value"
                    "updated value 2"
                },
                isContainedInCache = { true },
                retrieveAndUpdateCache = {
                    wasCalled = true
                    "from db 2"
                },
                persist = { }
            )
            wasCalled shouldBe false
        }
        should("also save unchanged value") {
            cut = StateFlowCache(cacheScope)
            cut.writeWithCache(
                key = "key",
                updater = { "updated value" },
                isContainedInCache = { false },
                retrieveAndUpdateCache = { null },
                persist = {}
            )
            var wasCalled = false
            cut.writeWithCache(
                key = "key",
                updater = { "updated value" },
                isContainedInCache = { true },
                retrieveAndUpdateCache = { null },
                persist = { wasCalled = true }
            )
            wasCalled shouldBe true
        }
        should("handle parallel manipulation") {
            cut = StateFlowCache(cacheScope)
            val database = MutableSharedFlow<String?>(replay = 3000)
            coroutineScope {
                repeat(1000) { i ->
                    launch {
                        cut.writeWithCache(
                            key = "key",
                            updater = { "$i" },
                            isContainedInCache = { false },
                            retrieveAndUpdateCache = { database.replayCache.lastOrNull() },
                            persist = { newValue -> database.emit(newValue) }
                        )
                    }
                }
            }
            database.replayCache shouldContainAll (0..99).map { it.toString() }
        }
        context("infinite cache not enabled") {
            should("remove from cache, when write cache time expired") {
                cut = StateFlowCache(cacheScope, cacheDuration = 30.milliseconds)
                cut.writeWithCache(
                    key = "key",
                    updater = { "updated value" },
                    isContainedInCache = { false },
                    retrieveAndUpdateCache = { null },
                    persist = { }
                )
                delay(50)
                var wasCalled = false
                cut.writeWithCache(
                    key = "key",
                    updater = { "updated value" },
                    isContainedInCache = { true },
                    retrieveAndUpdateCache = {
                        wasCalled = true
                        null
                    },
                    persist = { }
                )
                wasCalled shouldBe true
            }
        }
        context("infinite cache enabled") {
            should("never remove from cache") {
                cut = StateFlowCache(cacheScope, infiniteCache = true, cacheDuration = 10.milliseconds)
                cut.writeWithCache(
                    key = "key",
                    updater = { "updated value" },
                    isContainedInCache = { false },
                    retrieveAndUpdateCache = { null },
                    persist = { }
                )
                delay(30)
                var wasCalled = false
                cut.writeWithCache(
                    key = "key",
                    updater = { oldValue ->
                        oldValue shouldBe "updated value"
                        "updated value"
                    },
                    isContainedInCache = { true },
                    retrieveAndUpdateCache = {
                        wasCalled = true
                        null
                    },
                    persist = { }
                )
                wasCalled shouldBe false
            }
        }
    }
})