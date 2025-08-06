package net.folivo.trixnity.core

import io.kotest.matchers.shouldBe
import io.ktor.http.*
import net.folivo.trixnity.core.MatrixRegex.findMentions
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.util.Reference
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.*


class MatrixRegexTest : TrixnityBaseTest() {
    // User IDs
    private fun userIdTest(id: String, localpart: String, domain: String, expected: Boolean) {
        val text = "Hello $id :D"
        val result = findMentions(text)

        result.keys.any {
            text.substring(it) == id
        } shouldBe expected

        if (expected) {
            result.size shouldBe 1
            (result.entries.first { text.substring(it.key) == id }.value as Reference.User).userId shouldBe UserId(
                localpart,
                domain
            )
        } else {
            result.size shouldBe 0
        }
    }

    @Test
    fun shouldPassValidUserIdentifier() {
        userIdTest("@a9._=-/+:example.com", "a9._=-/+", "example.com", expected = true)
    }

    @Test
    fun shouldPassValidUserIdentifierWithLongDomain() {
        userIdTest(
            "@demo.test:example.eu.timp.mock.abc.xyz",
            "demo.test",
            "example.eu.timp.mock.abc.xyz",
            expected = true
        )
    }

    @Test
    fun shouldPassValidUserIdentifierWithSpecialCharacters() {
        userIdTest("@user:sub.example.com:8000", "user", "sub.example.com:8000", expected = true)
    }

    @Test
    fun shouldPassValidUserIdentifierWithIPV4() {
        userIdTest("@user:1.1.1.1", "user", "1.1.1.1", expected = true)
    }

    @Test
    fun shouldPassValidUserIdentifierWithIPV6() {
        userIdTest(
            "@user:[2001:0db8:85a3:0000:0000:8a2e:0370:7334]",
            "user",
            "[2001:0db8:85a3:0000:0000:8a2e:0370:7334]",
            expected = true
        )
    }

    @Test
    fun shouldFailUserOver255Bytes() {
        userIdTest("@${"users".repeat(50)}:example.com", "users".repeat(50), "example.com", expected = false)
    }

    @Test
    fun shouldFailUserLocalpartContainingIllegalSymbole() {
        userIdTest("@user&:example.com", "user&", "example.com", expected = false)
    }

    @Test
    fun shouldFailInvalidUserIPV6WithIllegalCharacters() {
        userIdTest("@user:[2001:8a2e:0370:733G]", "user", "[2001:8a2e:0370:733G]", expected = false)
    }

    // Room Alias
    private fun roomAliasTest(id: String, localpart: String, domain: String, expected: Boolean) {
        val text = "omw to $id now"
        val result = findMentions(text)

        result.keys.any {
            text.substring(it) == id
        } shouldBe expected

        if (expected) {
            result.size shouldBe 1
            (result.entries.first { text.substring(it.key) == id }.value as Reference.RoomAlias).roomAliasId shouldBe RoomAliasId(
                localpart,
                domain
            )
        } else {
            result.size shouldBe 0
        }
    }

    @Test
    fun shouldPassRoomAlias() {
        roomAliasTest("#a9._=-/+:example.com", "a9._=-/+", "example.com", expected = true)
    }

    @Test
    fun shouldPassRoomAliasWithPort() {
        roomAliasTest("#room:sub.example.com:8000", "room", "sub.example.com:8000", expected = true)
    }

    @Test
    fun shouldPassRoomAliasWithIPV4() {
        roomAliasTest("#room:1.1.1.1", "room", "1.1.1.1", expected = true)
    }

    @Test
    fun shouldPassRoomAliasWithIPV6() {
        roomAliasTest(
            "#room:[2001:0db8:85a3:0000:0000:8a2e:0370:7334]",
            "room",
            "[2001:0db8:85a3:0000:0000:8a2e:0370:7334]",
            expected = true
        )
    }

    @Test
    fun shouldFailRoomAliasOver255Bytes() {
        roomAliasTest("#${"alias".repeat(50)}:example.com", "alias".repeat(50), "example.com", expected = false)
    }

    @Test
    fun shouldFailRoomAliasWithIllegalSymboleInLocalpart() {
        roomAliasTest("#roo&m:example.com", "room&", "example.com", expected = false)
    }

    @Test
    fun failFailRoomAliasIPV6WithIllegalCharacters() {
        roomAliasTest("#room:[2001:8a2e:0370:733G]", "room", "[2001:8a2e:0370:733G]", expected = false)
    }

    // URIs
    private object UriTest {
        fun user(uri: String, localpart: String, domain: String, expected: Boolean) {
            val text = "Hello $uri :D"
            val result = findMentions(text)

            result.keys.any {
                text.substring(it) == uri
            } shouldBe expected

            if (expected) {
                result.size shouldBe 1
                (result.entries.first { text.substring(it.key) == uri }.value as Reference.User).userId shouldBe UserId(
                    localpart,
                    domain
                )
            } else {
                result.size shouldBe 0
            }
        }

        fun roomId(uri: String, id: String, expected: Boolean) {
            val text = "omw to $uri now"
            val result = findMentions(text)

            result.keys.any {
                text.substring(it) == uri
            } shouldBe expected

            if (expected) {
                result.size shouldBe 1
                (result.entries.first { text.substring(it.key) == uri }.value as Reference.Room).roomId shouldBe RoomId(id)
            }
        }

        fun roomAlias(uri: String, localpart: String, domain: String, expected: Boolean) {
            val text = "omw to $uri now"
            val result = findMentions(text)

            result.keys.any {
                text.substring(it) == uri
            } shouldBe expected

            if (expected) {
                result.size shouldBe 1
                (result.entries.first { text.substring(it.key) == uri }.value as Reference.RoomAlias).roomAliasId shouldBe RoomAliasId(
                    localpart,
                    domain
                )
            } else {
                result.size shouldBe 0
            }
        }

        fun event(uri: String, roomId: String, eventId: String, expected: Boolean) {
            val text = "You can find it at $uri :)"
            val result = findMentions(text)

            if (expected) {
                val value = result.values.singleOrNull()
                assertNotNull(value)
                assertIs<Reference.Event>(value)
                assertEquals(roomId, value.roomId!!.full)
                assertEquals(eventId, value.eventId.full)
            } else {
                assertEquals(
                    expected = emptyList(),
                    actual = result.values.toList(),
                )
            }
        }
    }

    // URIs: User ID
    @Test
    fun shouldPassUserURIWithActionQuery() {
        UriTest.user("matrix:u/user:example.com?action=chat", "user", "example.com", expected = true)
    }

    @Test
    fun shouldPassUserURIWithViaQuery() {
        UriTest.user("matrix:u/user:example.com?via=example.com", "user", "example.com", expected = true)
    }

    @Test
    fun shouldPassUserURIwithViaAndActionQuery() {
        UriTest.user("matrix:u/user:example.com?via=example.com&action=chat", "user", "example.com", expected = true)
    }

    @Test
    fun shouldPassUserURIWithActionAndViaQuery() {
        UriTest.user("matrix:u/user:example.com?action=chat&via=example.com", "user", "example.com", expected = true)
    }

    @Test
    fun shouldPassUserURI() {
        UriTest.user("matrix:u/user:example.com", "user", "example.com", expected = true)
    }

    // URIs: Room Alias
    @Test
    fun shouldPassRoomAliasURIWithActionQuery() {
        UriTest.roomAlias("matrix:r/room:example.com?action=join", "room", "example.com", expected = true)
    }

    @Test
    fun shouldPassRoomAliasURIWithViaQuery() {
        UriTest.roomAlias("matrix:r/room:example.com?via=example.com", "room", "example.com", expected = true)
    }

    @Test
    fun shouldPassRoomAliasURIWithViaAndActionQuery() {
        UriTest.roomAlias(
            "matrix:r/room:example.com?via=example.com&action=join",
            "room",
            "example.com",
            expected = true
        )
    }

    @Test
    fun shouldPassRoomAliasURIWIthActionAndViaQuery() {
        UriTest.roomAlias(
            "matrix:r/room:example.com?action=chat&via=example.com",
            "room",
            "example.com",
            expected = true
        )
    }

    @Test
    fun shouldPassRoomAliasURI() {
        UriTest.roomAlias("matrix:r/room:example.com", "room", "example.com", expected = true)
    }

    // URIs: Room ID
    @Test
    fun shouldPassRoomIdURIWithActionQuery() {
        UriTest.roomId("matrix:roomid/room:example.com?action=join", "!room:example.com", expected = true)
    }

    @Test
    fun shouldPassRoomIdURIWithUnkownQuery() {
        UriTest.roomId("matrix:roomid/room:example.com?actiooon=message", "!room:example.com", expected = true)
    }

    @Test
    fun shouldPassRoomIdURIWithIllegalQuery() {
        UriTest.roomId("matrix:roomid/room:example.com?actioné=messager", "!room:example.com", expected = true)
    }

    @Test
    fun shouldPassRoomIdURIWithReservedQuery() {
        UriTest.roomId("matrix:roomid/room:example.com?m.action=join", "!room:example.com", expected = true)
    }

    @Test
    fun shouldPassRoomIdURIWithViaQuery() {
        UriTest.roomId("matrix:roomid/room:example.com?via=example.com", "!room:example.com", expected = true)
    }

    @Test
    fun shouldPassRoomIdURIWithViaAndActionQuery() {
        UriTest.roomId(
            "matrix:roomid/room:example.com?via=example.com&action=join",
            "!room:example.com",
            expected = true
        )
    }

    @Test
    fun shouldPassRoomIdURIWithActionAndViaQuery() {
        UriTest.roomId(
            "matrix:roomid/room:example.com?action=chat&via=example.com",
            "!room:example.com",
            expected = true
        )
    }

    @Test
    fun shouldPassRoomIdURI() {
        UriTest.roomId("matrix:roomid/room:example.com", "!room:example.com", expected = true)
    }

    // URIs: Event ID
    @Test
    fun shouldPassEventURIWithActionQuery() {
        UriTest.event(
            "matrix:roomid/room:example.com/e/event?action=join",
            "!room:example.com",
            "\$event",
            expected = true
        )
    }

    @Test
    fun shouldPassEventURIWithViaQuery() {
        UriTest.event(
            "matrix:roomid/room:example.com/e/event?via=example.com",
            "!room:example.com",
            "\$event",
            expected = true
        )
    }

    @Test
    fun shouldPassEventURIWithViaAndActionQuery() {
        UriTest.event(
            "matrix:roomid/room:example.com/e/event?via=example.com&action=join",
            "!room:example.com",
            "\$event",
            expected = true
        )
    }

    @Test
    fun shouldPassEventURIWithActionAndViaQuery() {
        UriTest.event(
            "matrix:roomid/room:example.com/e/event?action=chat&via=example.com",
            "!room:example.com",
            "\$event",
            expected = true
        )
    }

    @Test
    fun shouldPassEventURI() {
        UriTest.event("matrix:roomid/room:example.com/e/event", "!room:example.com", "\$event", expected = true)
    }

    // Permalinks (matrix.to)
    object PermalinkTest {
        fun user(permalink: String, localpart: String, domain: String, expected: Boolean) {
            val text = "Hello $permalink :D"
            val result = findMentions(text)

            result.keys.any {
                text.substring(it) == permalink
            } shouldBe expected

            if (expected) {
                result.size shouldBe 1
                (result.entries.first { text.substring(it.key) == permalink }.value as Reference.User).userId shouldBe UserId(
                    localpart,
                    domain
                )
            } else {
                result.size shouldBe 0
            }
        }

        fun roomId(permalink: String, roomId: String, expected: Boolean) {
            val text = "omw to $permalink now"
            val result = findMentions(text)

            result.keys.any {
                text.substring(it) == permalink
            } shouldBe expected

            if (expected) {
                result.size shouldBe 1
                (result.entries.first { text.substring(it.key) == permalink }.value as Reference.Room).roomId shouldBe
                        RoomId(roomId)
            } else {
                result.size shouldBe 0
            }
        }

        fun roomAlias(permalink: String, localpart: String, domain: String, expected: Boolean) {
            val text = "omw to $permalink now"
            val result = findMentions(text)

            result.keys.any {
                text.substring(it) == permalink
            } shouldBe expected

            if (expected) {
                result.size shouldBe 1
                (result.entries.first { text.substring(it.key) == permalink }.value as Reference.RoomAlias).roomAliasId shouldBe RoomAliasId(
                    localpart,
                    domain
                )
            } else {
                result.size shouldBe 0
            }
        }

        fun event(permalink: String, roomId: String, eventId: String, expected: Boolean) {
            val text = "You can find it at $permalink :)"
            val result = findMentions(text)

            result.keys.any {
                text.substring(it) == permalink
            } shouldBe expected

            if (expected) {
                result.size shouldBe 1

                val mention = result.entries.first { text.substring(it.key) == permalink }.value
                if (mention !is Reference.Event) {
                    fail("Wrong Mention type")
                } else {
                    mention.eventId shouldBe EventId(eventId)
                    mention.roomId shouldBe RoomId(roomId)
                }
            } else {
                result.size shouldBe 0
            }
        }
    }

    // Permalink: User ID

    @Test
    fun shouldPassUserPermalink() {
        PermalinkTest.user("https://matrix.to/#/@user:example.com", "user", "example.com", expected = true)
    }

    @Test
    fun shouldPassEncodedUserPermalink() {
        PermalinkTest.user("https://matrix.to/#/%40alice%3Aexample.org", "alice", "example.org", expected = true)
    }

    // Permalink: Room Alias

    @Test
    fun shouldPassRoomAliasPermalink() {
        PermalinkTest.roomAlias("https://matrix.to/#/#room:example.com", "room", "example.com", expected = true)
    }

    @Test
    fun shouldPassEncodedRoomAliasPermalink() {
        PermalinkTest.roomAlias(
            "https://matrix.to/#/%23somewhere%3Aexample.org",
            "somewhere",
            "example.org",
            expected = true
        )
    }

    // Permalink: Room ID

    @Test
    fun shouldPassRoomIdPermalink() {
        PermalinkTest.roomId("https://matrix.to/#/!room:example.com", "!room:example.com", expected = true)
    }

    @Test
    fun shouldPassEncodedRoomIdPermalink() {
        PermalinkTest.roomId(
            "https://matrix.to/#/!room%3Aexample.com?via=elsewhere.ca",
            "!room:example.com",
            expected = true
        )
    }

    // Permalink: Event ID
    @Test
    fun shouldPassEventIDPermalink() {
        PermalinkTest.event(
            "https://matrix.to/#/!room:example.com/\$event",
            "!room:example.com",
            "\$event",
            expected = true
        )
    }

    @Test
    fun shouldPassEncodedEventIDPermalink() {
        PermalinkTest.event(
            "https://matrix.to/#/!room%3Aexample.com/%24event%3Aexample.org?via=elsewhere.ca",
            "!room:example.com",
            "\$event:example.org",
            expected = true
        )
    }

    // Parameters
    fun parameterTest(uri: String, params: Parameters, expected: Boolean) {
        val mentions = findMentions(uri)

        mentions.values.forEach {
            val parameters = when (it) {
                is Reference.Event -> it.parameters
                is Reference.Link -> null
                is Reference.Room -> it.parameters
                is Reference.RoomAlias -> it.parameters
                is Reference.User -> it.parameters
            }
            (parameters == params) shouldBe expected
        }
    }

    @Test
    fun shouldPassValidViaParameter() {
        parameterTest(
            "matrix:roomid/somewhere%3Aexample.org/%24event%3Aexample.org?via=elsewhere.ca",
            parametersOf("via" to listOf("elsewhere.ca")),
            expected = true
        )
    }

    @Test
    fun shouldPassActionParameter() {
        parameterTest(
            "matrix:roomid/room:example.com/e/event?via=example.com&action=join",
            parametersOf("action" to listOf("join"), "via" to listOf("example.com")),
            expected = true
        )
    }

    @Test
    fun shouldPassActionAndViaParameter() {
        parameterTest(
            "matrix:roomid/somewhere%3Aexample.org?action=chat&via=example.com",
            parametersOf("action" to listOf("chat"), "via" to listOf("example.com")),
            expected = true
        )
    }

    @Test
    fun shouldPassCustomParameter() {
        parameterTest(
            "matrix:r/somewhere:example.org?foo=bar",
            parametersOf("foo" to listOf("bar")),
            expected = true
        )
    }

    @Test
    fun shouldParseCustomParameterWithIllegalCharacter() {
        parameterTest(
            "matrix:u/mario:esempio.it?actionaté=mammamia",
            parametersOf("actionaté" to listOf("mammamia")),
            expected = true
        )
    }

    @Test
    fun shouldParseCustomParameterWithIllegalStart() {
        parameterTest(
            "matrix:u/user:homeserver.рф?m.vector=matrix",
            parametersOf("m.vector" to listOf("matrix")),
            expected = true
        )
    }

    @Test
    fun shouldParseCustomParametersWithLastOneBeingIllegal() {
        parameterTest(
            "matrix:u/user:example.com?foo=bar&actionaté=mammamia",
            parametersOf("foo" to listOf("bar"), "actionaté" to listOf("mammamia")),
            expected = true
        )
    }

    @Test
    fun `ignores overlaps`() {
        val content = "lorem @user:example.org ipsum https://matrix.to/#/@user:example.org?action=chat dolor matrix:u/user:example.org sit"
        // Links
        assertEquals(
            expected = "https://matrix.to/#/@user:example.org?action=chat",
            actual = content.substring(30..78),
        )
        assertEquals(
            expected = "matrix:u/user:example.org",
            actual = content.substring(86..110),
        )
        assertEquals(
            expected = mapOf(
                30..78 to Reference.User(UserId("@user:example.org"), parametersOf("action", "chat")),
                86..110 to Reference.User(UserId("@user:example.org")),
            ),
            actual = MatrixRegex.findLinkMentions(content)
        )
        // Ids
        assertEquals(
            expected = "@user:example.org",
            actual = content.substring(6..22),
        )
        assertEquals(
            expected = "@user:example.org",
            actual = content.substring(50..66),
        )
        assertEquals(
            expected = mapOf(
                6..22 to Reference.User(UserId("@user:example.org")),
                50..66 to Reference.User(UserId("@user:example.org")),
            ),
            actual = MatrixRegex.findIdMentions(content)
        )
        // Combined
        assertEquals(
            expected = mapOf(
                30..78 to Reference.User(UserId("@user:example.org"), parametersOf("action", "chat")),
                86..110 to Reference.User(UserId("@user:example.org")),
                6..22 to Reference.User(UserId("@user:example.org")),
            ),
            actual = MatrixRegex.findMentions(content)
        )
        assertEquals(
            expected = mapOf(
                9..44 to Reference.User(userId=UserId("@user:matrix.org")),
                92..171 to Reference.Room(roomId=RoomId("!WvOltebgJfkgHzhfpW:matrix.org"), parameters=parametersOf("via" to listOf("matrix.org", "imbitbu.de"))),
                199..323 to Reference.Event(roomId=RoomId("!WvOltebgJfkgHzhfpW:matrix.org"), eventId=EventId("\$KoEcMwZKqGpCeuMjAmt9zvmWgO72f7hDFkvfBMS479A"), parameters=parametersOf("via" to listOf("matrix.org", "imbitbu.de"))),
            ),
            actual = MatrixRegex.findMentions(
                "<a href=\"https://matrix.to/#/@user:matrix.org\">Some Username</a>: This is a user mention<br>" +
                        "https://matrix.to/#/!WvOltebgJfkgHzhfpW:matrix.org?via=matrix.org&via=imbitbu.de This is a room mention<br>" +
                        "https://matrix.to/#/!WvOltebgJfkgHzhfpW:matrix.org/\$KoEcMwZKqGpCeuMjAmt9zvmWgO72f7hDFkvfBMS479A?via=matrix.org&via=imbitbu.de This is an event mention"
            )
        )
    }

    @Test
    fun `finds regular links`() {
        assertEquals(
            expected = mapOf(
                19..65 to "https://en.wikipedia.org/wiki/Matrix_(protocol)",
            ),
            actual = MatrixRegex.findLinks(
                "I saw that online (https://en.wikipedia.org/wiki/Matrix_(protocol)), neat eh?"
            )
        )
    }
}
