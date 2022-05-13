package net.folivo.trixnity.core.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class EventId(val full: String)