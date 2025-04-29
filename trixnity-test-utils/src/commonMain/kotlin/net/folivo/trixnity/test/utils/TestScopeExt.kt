package net.folivo.trixnity.test.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@OptIn(ExperimentalCoroutinesApi::class)
val TestScope.testClock: Clock
    get() = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(currentTime)
    }