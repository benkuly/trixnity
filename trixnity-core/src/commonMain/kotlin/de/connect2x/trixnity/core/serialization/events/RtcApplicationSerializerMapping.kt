package de.connect2x.trixnity.core.serialization.events

import de.connect2x.trixnity.core.MSC4143
import de.connect2x.trixnity.core.model.events.m.rtc.RtcApplicationMember
import de.connect2x.trixnity.core.model.events.m.rtc.RtcApplicationSlot
import kotlin.reflect.KClass
import kotlinx.serialization.KSerializer

@MSC4143
interface RtcApplicationSerializerMapping<S : RtcApplicationSlot, M : RtcApplicationMember> {
    val type: String
    val applicationClass: KClass<out S>
    val applicationSerializer: KSerializer<S>
    val memberClass: KClass<out M>
    val memberSerializer: KSerializer<M>
}

@MSC4143
data class RtcApplicationSerializerMappingImpl<S : RtcApplicationSlot, M : RtcApplicationMember>(
    override val type: String,
    override val applicationClass: KClass<out S>,
    override val applicationSerializer: KSerializer<S>,
    override val memberClass: KClass<out M>,
    override val memberSerializer: KSerializer<M>,
) : RtcApplicationSerializerMapping<S, M>
