package de.connect2x.trixnity.core.model.events

interface Event<C : EventContent> {
    val content: C
}