package de.connect2x.trixnity.core.model.keys

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
value class MacValue(val value: String)