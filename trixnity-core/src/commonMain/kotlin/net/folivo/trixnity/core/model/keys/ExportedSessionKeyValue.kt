package net.folivo.trixnity.core.model.keys

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
value class ExportedSessionKeyValue(val value: String)