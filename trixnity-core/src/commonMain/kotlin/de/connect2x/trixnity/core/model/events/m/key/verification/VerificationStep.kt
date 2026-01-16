package de.connect2x.trixnity.core.model.events.m.key.verification

import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.model.events.ToDeviceEventContent

interface VerificationStep : MessageEventContent, ToDeviceEventContent {
    val transactionId: String?
}