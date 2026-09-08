package de.connect2x.trixnity.core.model.events.m.rtc

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.MSC4193
import kotlinx.serialization.Serializable

@MSC4193 @MSC4143 @Serializable data object PerMemberRtcEncryption : RtcEncryption
