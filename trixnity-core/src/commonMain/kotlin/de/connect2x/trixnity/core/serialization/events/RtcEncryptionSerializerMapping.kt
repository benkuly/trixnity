package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.model.events.m.rtc.RtcEncryption
import kotlin.reflect.KClass
import kotlinx.serialization.KSerializer

@MSC4143
interface RtcEncryptionSerializerMapping<T : RtcEncryption> {
    val type: String
    val kClass: KClass<out T>
    val serializer: KSerializer<T>
}

@MSC4143
data class RtcEncryptionSerializerMappingImpl<T : RtcEncryption>(
    override val type: String,
    override val kClass: KClass<out T>,
    override val serializer: KSerializer<T>,
) : RtcEncryptionSerializerMapping<T>
