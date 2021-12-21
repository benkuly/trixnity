package net.folivo.trixnity.client.verification

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import net.folivo.trixnity.client.verification.ActiveVerificationState.Cancel
import net.folivo.trixnity.client.verification.ActiveVerificationState.Done
import net.folivo.trixnity.core.model.events.m.key.verification.StartEventContent
import net.folivo.trixnity.core.serialization.canonicalJson
import net.folivo.trixnity.olm.OlmUtility
import net.folivo.trixnity.olm.freeAfter
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun isVerificationRequestActive(timestamp: Long): Boolean {
    val timegap = Clock.System.now() - Instant.fromEpochMilliseconds(timestamp)
    return timegap < 10.minutes && timegap > (-5).minutes
}

fun isVerificationRequestActive(timestamp: Long, state: ActiveVerificationState): Boolean {
    return state !is Done && state !is Cancel && isVerificationRequestActive(timestamp)
}

internal suspend fun createSasCommitment(
    publicKey: String,
    content: StartEventContent,
    json: Json
): String {
    val jsonObject = json.encodeToJsonElement(content)
    require(jsonObject is JsonObject)
    val canonicalJson = canonicalJson(jsonObject)
    return freeAfter(OlmUtility.create()) { olmUtil ->
        olmUtil.sha256(publicKey + canonicalJson)
    }
}