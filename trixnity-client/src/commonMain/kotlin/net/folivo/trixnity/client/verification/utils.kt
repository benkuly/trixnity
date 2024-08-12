package net.folivo.trixnity.client.verification

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import net.folivo.trixnity.client.verification.ActiveVerificationState.*
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStartEventContent
import net.folivo.trixnity.core.serialization.canonicalJsonString
import net.folivo.trixnity.olm.OlmUtility
import net.folivo.trixnity.olm.freeAfter
import kotlin.time.Duration.Companion.minutes

fun isVerificationRequestActive(timestamp: Long, clock: Clock): Boolean {
    val timegap = clock.now() - Instant.fromEpochMilliseconds(timestamp)
    return timegap < 10.minutes && timegap > (-5).minutes
}

fun isVerificationRequestActive(timestamp: Long, clock: Clock, state: ActiveVerificationState): Boolean {
    return state !is Done && state !is Cancel
            && state !is AcceptedByOtherDevice && state !is Undefined
            && isVerificationRequestActive(timestamp, clock)
}

fun isVerificationTimedOut(timestamp: Long, clock: Clock, state: ActiveVerificationState): Boolean {
    return state !is Done && state !is Cancel
            && state !is AcceptedByOtherDevice && state !is Undefined
            && !isVerificationRequestActive(timestamp, clock)
}

internal suspend fun createSasCommitment(
    publicKey: String,
    content: VerificationStartEventContent,
    json: Json
): String {
    val jsonObject = json.encodeToJsonElement(content)
    require(jsonObject is JsonObject)
    val canonicalJson = canonicalJsonString(jsonObject)
    return freeAfter(OlmUtility.create()) { olmUtil ->
        olmUtil.sha256(publicKey + canonicalJson)
    }
}