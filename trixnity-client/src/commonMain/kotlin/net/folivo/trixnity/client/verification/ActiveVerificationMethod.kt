package net.folivo.trixnity.client.verification

import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStartEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStep

abstract class ActiveVerificationMethod {
    abstract val startEventContent: VerificationStartEventContent
    internal abstract suspend fun handleVerificationStep(step: VerificationStep, isOurOwn: Boolean)
}