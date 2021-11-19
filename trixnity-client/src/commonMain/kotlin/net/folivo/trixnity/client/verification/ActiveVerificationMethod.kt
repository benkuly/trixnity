package net.folivo.trixnity.client.verification

import net.folivo.trixnity.core.model.events.m.key.verification.StartEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStep

abstract class ActiveVerificationMethod {
    abstract val startEventContent: StartEventContent
    internal abstract suspend fun handleVerificationStep(step: VerificationStep, isOurOwn: Boolean)
}