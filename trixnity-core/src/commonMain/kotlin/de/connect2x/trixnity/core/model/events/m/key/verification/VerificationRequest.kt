package de.connect2x.trixnity.core.model.events.m.key.verification

interface VerificationRequest {
    val fromDevice: String
    val methods: Set<VerificationMethod>
}