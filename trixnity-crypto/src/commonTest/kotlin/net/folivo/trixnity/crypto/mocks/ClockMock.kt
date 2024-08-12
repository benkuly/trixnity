package net.folivo.trixnity.crypto.mocks

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class ClockMock : Clock {
    var nowValue: Instant = Instant.fromEpochMilliseconds(24242424)
    override fun now(): Instant = nowValue
}