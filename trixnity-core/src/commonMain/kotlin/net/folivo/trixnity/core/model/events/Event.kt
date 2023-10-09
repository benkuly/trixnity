package net.folivo.trixnity.core.model.events

interface Event<C : EventContent> {
    val content: C
}