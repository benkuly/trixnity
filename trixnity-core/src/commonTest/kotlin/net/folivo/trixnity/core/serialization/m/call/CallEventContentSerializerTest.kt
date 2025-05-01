package net.folivo.trixnity.core.serialization.m.call

import net.folivo.trixnity.core.model.events.m.call.CallEventContent
import net.folivo.trixnity.core.model.events.m.call.CallEventContent.Answer.Answer
import net.folivo.trixnity.core.model.events.m.call.CallEventContent.Answer.AnswerType
import net.folivo.trixnity.core.model.events.m.call.CallEventContent.Candidates.Candidate
import net.folivo.trixnity.core.model.events.m.call.CallEventContent.Invite.Offer
import net.folivo.trixnity.core.model.events.m.call.CallEventContent.Invite.OfferType
import net.folivo.trixnity.core.model.events.m.call.CallEventContent.Hangup.Reason
import net.folivo.trixnity.core.model.events.m.call.CallEventContent.Negotiate.Description
import net.folivo.trixnity.core.model.events.m.call.CallEventContent.Negotiate.DescriptionType
import net.folivo.trixnity.core.model.events.m.call.Purpose
import net.folivo.trixnity.core.model.events.m.call.StreamMetadata
import net.folivo.trixnity.core.serialization.createMatrixEventJson
import net.folivo.trixnity.core.serialization.trimToFlatJson
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CallEventContentSerializerTest : TrixnityBaseTest() {

    private val json = createMatrixEventJson()

    ////////////////// m.call.invite //////////////////

    private val testInvite = CallEventContent.Invite(
        version = "1",
        callId = "0123",
        offer = Offer(sdp = """
            v=0
            o=- 6584580628695956864 2 IN IP4 127.0.0.1
            """.trimIndent(),
            type = OfferType.OFFER),
        lifetime = 30000,
        sdpStreamMetadata = mapOf(
            "271828182845" to StreamMetadata(Purpose.SCREENSHARE),
            "314159265358" to StreamMetadata(Purpose.USERMEDIA),
        )
    )

    private val serializedInvite = """{
        "version": "1",
        "call_id": "0123",
        "lifetime": 30000,
        "offer": {
            "sdp": "v=0\no=- 6584580628695956864 2 IN IP4 127.0.0.1",
            "type": "offer"
        },
        "sdp_stream_metadata": {
            "271828182845": {
                "purpose": "m.screenshare",
                "audio_muted": false,
                "video_muted": false
            },
            "314159265358": {
                "purpose": "m.usermedia",
                "audio_muted": false,
                "video_muted": false
            }
        }
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

    ////////////////// m.call.candidates //////////////////

    private val testCandidates = CallEventContent.Candidates(
        version = "0",
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
    )

    private val serializedCandidates = """{
        "version": 0,
        "call_id": "0123",
        "candidates": [
            {"candidate":"candidate:423458654 1 udp 2130706431 192.168.0.100 51008 typ host generation 0 ufrag 3f8a","sdpMLineIndex":0,"sdpMid":"audio"},
            {"candidate":"candidate:423458654 2 udp 2130706431 192.168.0.100 51009 typ host generation 0 ufrag 4839","sdpMLineIndex":1,"sdpMid":"audio"}
        ]
    }""".trimToFlatJson()

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

    ////////////////// m.call.answer //////////////////

    private val testAnswer = CallEventContent.Answer(
        version = "0",
        callId = "0123",
        partyId = null,
        answer = Answer(sdp = "v=0", type = AnswerType.ANSWER),
        sdpStreamMetadata = null,
    )
    private val serializedAnswer = """{
        "version": 0,
        "call_id": "0123",
        "answer": {"sdp":"v=0","type":"answer"}
    }""".trimToFlatJson()

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

    ////////////////// m.call.hangup //////////////////

    private val testHangup = CallEventContent.Hangup(
        callId = "0123",
        version = "0",
        partyId = null,
        reason = Reason.INVITE_TIMEOUT
    )

    private val serializedHangup = """{
        "version": 0,
        "call_id": "0123",
        "reason": "invite_timeout"
    }""".trimToFlatJson()

    @Test
    fun shouldSerializeCallHangup() {
        val result = json.encodeToString(testHangup)
        assertEquals(serializedHangup, result)
    }

    @Test
    fun shouldDeserializeCallHangup() {
        val result: CallEventContent.Hangup = json.decodeFromString(serializedHangup)
        assertEquals(testHangup, result)
    }

    ////////////////// m.call.negotiate //////////////////

    private val testNegotiate = CallEventContent.Negotiate(
        callId = "0123",
        partyId = "67890",
        description = Description("v=0", DescriptionType.OFFER),
        lifetime = 10000,
        sdpStreamMetadata = mapOf(
            "271828182845" to StreamMetadata(Purpose.SCREENSHARE),
            "314159265358" to StreamMetadata(Purpose.USERMEDIA),
        ),
    )
    private val serializedNegotiate = """{
        "version": "1",
        "call_id": "0123",
        "party_id": "67890",
        "description": {
            "sdp": "v=0",
            "type": "offer"
        },
        "lifetime": 10000,
        "sdp_stream_metadata": {
            "271828182845": {
                "purpose": "m.screenshare",
                "audio_muted": false,
                "video_muted": false
            },
            "314159265358": {
                "purpose": "m.usermedia",
                "audio_muted": false,
                "video_muted": false
            }
        }
    }""".trimToFlatJson()

    @Test
    fun shouldSerializeCallNegotiate() {
        val result = json.encodeToString(testNegotiate)
        assertEquals(serializedNegotiate, result)
    }

    @Test
    fun shouldDeserializeCallNegotiate() {
        val result: CallEventContent.Negotiate = json.decodeFromString(serializedNegotiate)
        assertEquals(testNegotiate, result)
    }

    ////////////////// m.call.reject //////////////////

    private val testReject = CallEventContent.Reject(callId = "0123", partyId = "23423")

    private val serializedReject = """{
        "version": "1",
        "call_id": "0123",
        "party_id": "23423"
    }""".trimToFlatJson()

    @Test
    fun shouldSerializeCallReject() {
        val result = json.encodeToString(testReject)
        assertEquals(serializedReject, result)
    }

    @Test
    fun shouldDeserializeCallReject() {
        val result: CallEventContent.Reject = json.decodeFromString(serializedReject)
        assertEquals(testReject, result)
    }

    ////////////////// m.call.select_answer //////////////////

    private val testSelectAnswer = CallEventContent.SelectAnswer(
        version = "0",
        callId = "0123",
        partyId = "23423",
        selectedPartyId = "67890"
    )

    private val serializedSelectAnswer = """{
        "version": 0,
        "call_id": "0123",
        "party_id": "23423",
        "selected_party_id": "67890"
    }""".trimToFlatJson()

    @Test
    fun shouldSerializeCallSelectAnswer() {
        val result = json.encodeToString(testSelectAnswer)
        assertEquals(serializedSelectAnswer, result)
    }

    @Test
    fun shouldDeserializeCallSelectAnswer() {
        val result: CallEventContent.SelectAnswer = json.decodeFromString(serializedSelectAnswer)
        assertEquals(testSelectAnswer, result)
    }

    ////////////////// m.call.sdp_stream_metadata_changed //////////////////

    private val testSdpStreamMetadataChanged = CallEventContent.SdpStreamMetadataChanged(
        callId = "0123",
        partyId = "67890",
        sdpStreamMetadata = mapOf(
            "271828182845" to StreamMetadata(Purpose.SCREENSHARE, audioMuted = true),
            "314159265358" to StreamMetadata(Purpose.USERMEDIA, videoMuted = true),
        ),
    )
    private val serializedSdpStreamMetadataChanged = """{
        "version": "1",
        "call_id": "0123",
        "party_id": "67890",
        "sdp_stream_metadata": {
            "271828182845": {
                "purpose": "m.screenshare",
                "audio_muted": true,
                "video_muted": false
            },
            "314159265358": {
                "purpose": "m.usermedia",
                "audio_muted": false,
                "video_muted": true
            }
        }
    }""".trimToFlatJson()

    @Test
    fun shouldDeserializeSdpStreamMetadataChanged() {
        val result: CallEventContent.SdpStreamMetadataChanged = json.decodeFromString(serializedSdpStreamMetadataChanged)
        assertEquals(testSdpStreamMetadataChanged, result)
    }

    @Test
    fun shouldSerializeSdpStreamMetadataChanged() {
        val result = json.encodeToString(testSdpStreamMetadataChanged)
        assertEquals(serializedSdpStreamMetadataChanged, result)
    }
}
