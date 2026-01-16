package de.connect2x.trixnity.core.model.events

import kotlin.reflect.KClass

data class EventType(val kClass: KClass<out EventContent>?, val name: String) {
    override fun toString(): String = name
}