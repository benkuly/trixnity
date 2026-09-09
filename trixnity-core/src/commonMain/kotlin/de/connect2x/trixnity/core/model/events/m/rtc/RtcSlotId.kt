package de.connect2x.trixnity.core.model.events.m.rtc

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class RtcSlotId private constructor(val full: String) {
    constructor(applicationType: String, applicationSlotId: String) : this("${applicationType}#${applicationSlotId}")
}
