package de.connect2x.trixnity.core.model.events.m.rtc

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4195
import io.ktor.http.Url
import kotlinx.serialization.Serializable

@MSC4143 @MSC4195 @Serializable data class LiveKitRtcTransport(val url: Url) : RtcTransport
