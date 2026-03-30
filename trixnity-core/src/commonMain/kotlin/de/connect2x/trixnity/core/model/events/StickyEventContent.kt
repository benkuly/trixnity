package de.connect2x.trixnity.core.model.events

import de.connect2x.trixnity.core.MSC4354

@MSC4354
interface StickyEventContent {
    val stickyKey: String?
}
