package net.folivo.trixnity.core.serialization.m.call

import kotlinx.serialization.encodeToString
import net.folivo.trixnity.core.model.events.m.call.CallEventContent
import net.folivo.trixnity.core.model.events.m.call.CallEventContent.Answer.Answer
import net.folivo.trixnity.core.model.events.m.call.CallEventContent.Answer.AnswerType
import net.folivo.trixnity.core.model.events.m.call.CallEventContent.Candidates.Candidate
import net.folivo.trixnity.core.model.events.m.call.CallEventContent.Invite.Offer
import net.folivo.trixnity.core.model.events.m.call.CallEventContent.Invite.OfferType
import net.folivo.trixnity.core.model.events.m.call.CallEventContent.Hangup.Reason
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.trimToFlatJson
import kotlin.test.Test
import kotlin.test.assertEquals

class CallEventContentSerializerTest {

    private val json = createMatrixEventJson()

    private val testInvite = CallEventContent.Invite(
        callId = "0123",
        offer = Offer(sdp = """
            v=0
            o=- 6584580628695956864 2 IN IP4 127.0.0.1
            """.trimIndent(),
            type = OfferType.OFFER),
        lifetime = 30000,
        version = 0,
    )

    private val serializedInvite =
        """{
            "version":0,
            "call_id":"0123",
            "lifetime":30000,
            "offer":{"sdp":"v=0\no=- 6584580628695956864 2 IN IP4 127.0.0.1","type":"offer"}
        }""".trimToFlatJson()

    private val testCandidates = CallEventContent.Candidates(
        callId = "0123",
        candidates = listOf(
            Candidate(
                candidate = "candidate:423458654 1 udp 2130706431 192.168.0.100 51008 typ host generation 0 ufrag 3f8a",
                sdpMid = "audio",
                sdpMLineIndex = 0
            ),
            Candidate(
                candidate = "candidate:423458654 2 udp 2130706431 192.168.0.100 51009 typ host generation 0 ufrag 4839",
                sdpMid = "audio",
                sdpMLineIndex = 1
            ),
        ),
        version = 0,
    )

    private val serializedCandidates = """{
            "version":0,
            "call_id":"0123",
            "candidates":[
                {"candidate":"candidate:423458654 1 udp 2130706431 192.168.0.100 51008 typ host generation 0 ufrag 3f8a","sdpMLineIndex":0,"sdpMid":"audio"},
                {"candidate":"candidate:423458654 2 udp 2130706431 192.168.0.100 51009 typ host generation 0 ufrag 4839","sdpMLineIndex":1,"sdpMid":"audio"}
            ]
        }""".trimToFlatJson()

    private val testAnswer = CallEventContent.Answer(
        answer = Answer(sdp = "v=0", type = AnswerType.ANSWER),
        callId = "0123",
        version = 0,
    )
    private val serializedAnswer = """{
            "version":0,
            "call_id":"0123",
            "answer":{"sdp":"v=0","type":"answer"}
        }""".trimToFlatJson()

    @Test
    fun shouldSerializeCallInvite() {
        val result = json.encodeToString(testInvite)
        assertEquals(serializedInvite, result)
    }

    @Test
    fun shouldDeserializeCallInvite() {
        val result: CallEventContent.Invite = json.decodeFromString(serializedInvite)
        assertEquals(testInvite, result)
    }

    @Test
    fun shouldSerializeCallCandidates() {
        val result = json.encodeToString(testCandidates)
        assertEquals(serializedCandidates, result)
    }

    @Test
    fun shouldDeserializeCallCandidates() {
        val result: CallEventContent.Candidates = json.decodeFromString(serializedCandidates)
        assertEquals(testCandidates, result)
    }

    @Test
    fun shouldSerializeCallAnswer() {
        val result = json.encodeToString(testAnswer)
        assertEquals(serializedAnswer, result)
    }

    @Test
    fun shouldDeserializeCallAnswer() {
        val result: CallEventContent.Answer = json.decodeFromString(serializedAnswer)
        assertEquals(testAnswer, result)
    }

    @Test
    fun shouldSerializeCallHangup() {
        val testHangup = CallEventContent.Hangup(version = 0, callId = "0123", reason = Reason.INVITE_TIMEOUT)
        val result = json.encodeToString(testHangup)
        assertEquals("""{"version":0,"call_id":"0123","reason":"invite_timeout"}""", result)
    }

    @Test
    fun shouldDeserializeCallHangup() {
        val result: CallEventContent.Hangup = json.decodeFromString("""{"call_id":"0123","reason":"ice_failed","version":0}""")
        assertEquals(CallEventContent.Hangup(version = 0, callId = "0123", reason = Reason.ICE_FAILED), result)
    }
}
