package de.connect2x.trixnity.core.model.events.m.secretstorage

import kotlinx.serialization.json.JsonElement
import de.connect2x.trixnity.core.model.events.GlobalAccountDataEventContent

interface SecretEventContent : GlobalAccountDataEventContent {
    // Yeah this is messy, but is due to the spec, which does not allow type safe deserialization of these events.
    val encrypted: Map<String, JsonElement>
}