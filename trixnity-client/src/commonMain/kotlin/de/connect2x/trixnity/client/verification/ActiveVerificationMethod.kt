package de.connect2x.trixnity.client.verification

import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationStartEventContent
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationStep

abstract class ActiveVerificationMethod {
    abstract val startEventContent: VerificationStartEventContent
    internal abstract suspend fun handleVerificationStep(step: VerificationStep, isOurOwn: Boolean)
}