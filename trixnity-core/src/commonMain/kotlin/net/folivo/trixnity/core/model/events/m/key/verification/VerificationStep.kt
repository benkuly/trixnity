package net.folivo.trixnity.core.model.events.m.key.verification

import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.ToDeviceEventContent

interface VerificationStep : MessageEventContent, ToDeviceEventContent {
    val relatesTo: VerificationStepRelatesTo?
    val transactionId: String?
}