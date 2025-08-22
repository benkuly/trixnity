package net.folivo.trixnity.crypto.mocks

import kotlin.time.Clock
import kotlin.time.Instant

class ClockMock : Clock {
    var nowValue: Instant = Instant.fromEpochMilliseconds(24242424)
    override fun now(): Instant = nowValue
}