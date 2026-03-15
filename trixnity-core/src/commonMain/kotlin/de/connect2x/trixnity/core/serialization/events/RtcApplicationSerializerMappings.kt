@file:OptIn(de.connect2x.trixnity.core.MSC4143::class)

package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.model.events.m.rtc.CallApplication
import de.connect2x.trixnity.core.model.events.m.rtc.RtcApplication
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

@MSC4143
interface RtcApplicationSerializerMappings : Set<RtcApplicationSerializerMapping<*>> {
    companion object
}

@MSC4143
data class RtcApplicationSerializerMapping<T : RtcApplication>(
    val type: String,
    val kClass: KClass<out T>,
    val serializer: KSerializer<T>,
)

@MSC4143
class RtcApplicationSerializerMappingsBuilder {
    val mappings = mutableSetOf<RtcApplicationSerializerMapping<*>>()

    @MSC4143
    fun build(): RtcApplicationSerializerMappings =
        object : RtcApplicationSerializerMappings,
            Set<RtcApplicationSerializerMapping<*>> by mappings {}
}

@MSC4143
fun createRtcApplicationSerializerMappings(
    builder: RtcApplicationSerializerMappingsBuilder.() -> Unit,
): RtcApplicationSerializerMappings = RtcApplicationSerializerMappingsBuilder().apply(builder).build()

@MSC4143
inline fun <reified T : RtcApplication> RtcApplicationSerializerMappingsBuilder.of(type: String) {
    mappings.add(RtcApplicationSerializerMapping(type, T::class, serializer<T>()))
}

private val defaultRtcApplicationSerializerMappings: RtcApplicationSerializerMappings =
    createRtcApplicationSerializerMappings {
        of<CallApplication>("m.call")
    }

@MSC4143
val RtcApplicationSerializerMappings.Companion.default: RtcApplicationSerializerMappings
    get() = defaultRtcApplicationSerializerMappings
