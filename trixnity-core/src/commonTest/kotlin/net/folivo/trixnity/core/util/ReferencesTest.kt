package net.folivo.trixnity.core.util

import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.string.shouldHaveLength
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.util.References.findIdReferences
import net.folivo.trixnity.core.util.References.findLinkReferences
import net.folivo.trixnity.core.util.References.findReferences
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReferencesTest : TrixnityBaseTest() {

    @Test
    fun shouldPassValidUserIdentifier() {
        TestHelper.userId("@a9._=-/+:example.com", expected = true)
        TestHelper.userId("@a9._=-/+:localhost", expected = true)
    }

    @Test
    fun shouldPassValidUserIdentifierWithLongDomain() {
        TestHelper.userId(
            "@demo.test:example.eu.timp.mock.abc.xyz",
            expected = true
        )
    }

    @Test
    fun shouldPassValidUserIdentifierWithSpecialCharacters() {
        TestHelper.userId("@user:sub.example.com:8000", expected = true)
    }

    @Test
    fun shouldPassValidUserIdentifierWithIPV4() {
        TestHelper.userId("@user:1.1.1.1", expected = true)
    }

    @Test
    fun shouldPassValidUserIdentifierWithIPV6() {
        TestHelper.userId(
            "@user:[2001:0db8:85a3:0000:0000:8a2e:0370:7334]",
            expected = true
        )
    }

    @Test
    fun shouldFailUserOver255Bytes() {
        TestHelper.userId("@${"users".repeat(50)}:example.com", expected = false)
    }

    @Test
    fun shouldFailUserLocalpartContainingUppsercase() {
        TestHelper.userId("@User:example.com", expected = false)
    }

    @Test
    fun shouldFailUserLocalpartContainingIllegalSymbole() {
        TestHelper.userId("@user&:example.com", expected = false)
    }

    @Test
    fun shouldFailInvalidUserIPV6WithIllegalCharacters() {
        TestHelper.userId("@user:[2001:8a2e:0370:733G]", expected = false)
    }

    @Test
    fun shouldPassRoomAlias() {
        TestHelper.roomAliasId("#a9._=-+:example.com", expected = true)
    }

    @Test
    fun shouldPassRoomAliasWithPort() {
        TestHelper.roomAliasId("#room:sub.example.com:8000", expected = true)
    }

    @Test
    fun shouldPassRoomAliasWithIPV4() {
        TestHelper.roomAliasId("#room:1.1.1.1", expected = true)
    }

    @Test
    fun shouldPassRoomAliasWithIPV6() {
        TestHelper.roomAliasId(
            "#room:[2001:0db8:85a3:0000:0000:8a2e:0370:7334]",
            expected = true
        )
    }

    @Test
    fun shouldFailRoomAliasOver255Bytes() {
        TestHelper.roomAliasId("#${"alias".repeat(50)}:example.com", expected = false)
    }

    @Test
    fun shouldFailRoomAliasWithIllegalSymboleInLocalpart() {
        TestHelper.roomAliasId("#r/m:example.com", expected = false)
    }

    @Test
    fun failFailRoomAliasIPV6WithIllegalCharacters() {
        TestHelper.roomAliasId("#room:[2001:8a2e:0370:733G]", expected = false)
    }

    // URIs: User ID
    @Test
    fun shouldPassUserURIWithActionQuery() {
        TestHelper.userId("matrix:u/user:example.com?action=chat", "@user:example.com", expected = true)
    }

    @Test
    fun shouldPassUserURIWithViaQuery() {
        TestHelper.userId("matrix:u/user:example.com?via=example.com", "@user:example.com", expected = true)
    }

    @Test
    fun shouldPassUserURIwithViaAndActionQuery() {
        TestHelper.userId("matrix:u/user:example.com?via=example.com&action=chat", "@user:example.com", expected = true)
    }

    @Test
    fun shouldPassUserURIWithActionAndViaQuery() {
        TestHelper.userId("matrix:u/user:example.com?action=chat&via=example.com", "@user:example.com", expected = true)
    }

    @Test
    fun shouldPassUserURI() {
        TestHelper.userId("matrix:u/user:example.com", "@user:example.com", expected = true)
    }

    // URIs: Room Alias
    @Test
    fun shouldPassRoomAliasURIWithActionQuery() {
        TestHelper.roomAliasId("matrix:r/room:example.com?action=join", "#room:example.com", expected = true)
    }

    @Test
    fun shouldPassRoomAliasURIWithViaQuery() {
        TestHelper.roomAliasId("matrix:r/room:example.com?via=example.com", "#room:example.com", expected = true)
    }

    @Test
    fun shouldPassRoomAliasURIWithViaAndActionQuery() {
        TestHelper.roomAliasId(
            "matrix:r/room:example.com?via=example.com&action=join",
            "#room:example.com",
            expected = true
        )
    }

    @Test
    fun shouldPassRoomAliasURIWIthActionAndViaQuery() {
        TestHelper.roomAliasId(
            "matrix:r/room:example.com?action=chat&via=example.com",
            "#room:example.com",
            expected = true
        )
    }

    @Test
    fun shouldPassRoomAliasURI() {
        TestHelper.roomAliasId("matrix:r/room:example.com", "#room:example.com", expected = true)
    }

    // URIs: Room ID
    @Test
    fun shouldPassRoomIdURIWithActionQuery() {
        TestHelper.roomId("matrix:roomid/room:example.com?action=join", "!room:example.com", expected = true)
    }

    @Test
    fun shouldPassRoomIdURIWithUnkownQuery() {
        TestHelper.roomId("matrix:roomid/room:example.com?actiooon=message", "!room:example.com", expected = true)
    }

    @Test
    fun shouldPassRoomIdURIWithIllegalQuery() {
        TestHelper.roomId("matrix:roomid/room:example.com?actioné=messager", "!room:example.com", expected = true)
    }

    @Test
    fun shouldPassRoomIdURIWithReservedQuery() {
        TestHelper.roomId("matrix:roomid/room:example.com?m.action=join", "!room:example.com", expected = true)
    }

    @Test
    fun shouldPassRoomIdURIWithViaQuery() {
        TestHelper.roomId("matrix:roomid/room:example.com?via=example.com", "!room:example.com", expected = true)
    }

    @Test
    fun shouldPassRoomIdURIWithViaAndActionQuery() {
        TestHelper.roomId(
            "matrix:roomid/room:example.com?via=example.com&action=join",
            "!room:example.com",
            expected = true
        )
    }

    @Test
    fun shouldPassRoomIdURIWithActionAndViaQuery() {
        TestHelper.roomId(
            "matrix:roomid/room:example.com?action=chat&via=example.com",
            "!room:example.com",
            expected = true
        )
    }

    @Test
    fun shouldPassRoomIdURI() {
        TestHelper.roomId("matrix:roomid/room:example.com", "!room:example.com", expected = true)
    }

    @Test
    fun `parses matrix protocol roomid v12 links`() {
        TestHelper.roomId(
            "matrix:roomid/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            "!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            expected = true
        )
        TestHelper.roomId(
            "matrix:roomid/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE?via=example.org&action=join&via=elsewhere.ca",
            "!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            expected = true
        )
    }

    // URIs: Event ID
    @Test
    fun shouldPassEventURIWithActionQuery() {
        TestHelper.eventId(
            "matrix:roomid/room:example.com/e/event?action=join",
            "!room:example.com",
            "\$event",
            expected = true
        )
    }

    @Test
    fun shouldPassEventURIWithViaQuery() {
        TestHelper.eventId(
            "matrix:roomid/room:example.com/e/event?via=example.com",
            "!room:example.com",
            "\$event",
            expected = true
        )
    }

    @Test
    fun shouldPassEventURIWithViaAndActionQuery() {
        TestHelper.eventId(
            "matrix:roomid/room:example.com/e/event?via=example.com&action=join",
            "!room:example.com",
            "\$event",
            expected = true
        )
    }

    @Test
    fun shouldPassEventURIWithActionAndViaQuery() {
        TestHelper.eventId(
            "matrix:roomid/room:example.com/e/event?action=chat&via=example.com",
            "!room:example.com",
            "\$event",
            expected = true
        )
    }

    @Test
    fun shouldPassEventURI() {
        TestHelper.eventId("matrix:roomid/room:example.com/e/event", "!room:example.com", "\$event", expected = true)
    }

    @Test
    fun `parses matrix protocol event v12 links`() {
        TestHelper.eventId(
            "matrix:roomid/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE/e/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            "!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            "\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            expected = true
        )
        TestHelper.eventId(
            "matrix:roomid/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE/e/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE?via=example.org&action=join&via=elsewhere.ca",
            "!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            "\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            expected = true
        )
    }

    // URIs: links
    @Test
    fun `finds regular links`() {
        TestHelper.link("https://en.wikipedia.org/wiki/Matrix_(protocol)", true)
        TestHelper.link("wikipedia.org", true)
    }

    @Test
    fun `fails on invalid links`() {
        TestHelper.link("invalid-link", false)
        TestHelper.link("matrix:group/group:example.com", false)
    }

    // Permalink: User ID

    @Test
    fun shouldPassUserPermalink() {
        TestHelper.userId("https://matrix.to/#/@user:example.com", "@user:example.com", expected = true)
    }

    @Test
    fun shouldPassEncodedUserPermalink() {
        TestHelper.userId("https://matrix.to/#/%40alice%3Aexample.org", "@alice:example.org", expected = true)
    }

    // Permalink: Room Alias

    @Test
    fun shouldPassRoomAliasPermalink() {
        TestHelper.roomAliasId("https://matrix.to/#/#room:example.com", "#room:example.com", expected = true)
    }

    @Test
    fun shouldPassEncodedRoomAliasPermalink() {
        TestHelper.roomAliasId(
            "https://matrix.to/#/%23somewhere%3Aexample.org",
            "#somewhere:example.org",
            expected = true
        )
    }

    // Permalink: Room ID

    @Test
    fun shouldPassRoomIdPermalink() {
        TestHelper.roomId("https://matrix.to/#/!room:example.com", "!room:example.com", expected = true)
    }

    @Test
    fun shouldPassEncodedRoomIdPermalink() {
        TestHelper.roomId(
            "https://matrix.to/#/!room%3Aexample.com?via=elsewhere.ca",
            "!room:example.com",
            expected = true
        )
    }

    @Test
    fun `parses matrixto roomid v12 links`() {
        TestHelper.roomId(
            "https://matrix.to/#/!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            "!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            expected = true
        )
        TestHelper.roomId(
            "https://matrix.to/#%2F!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            "!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            expected = true
        )
        TestHelper.roomId(
            "https://matrix.to/#/!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE?via=example.org&action=join&via=elsewhere.ca",
            "!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            expected = true
        )
        TestHelper.roomId(
            "https://matrix.to/#%2F!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE%3Fvia%3Dexample.org%26action%3Djoin%26via%3Delsewhere.ca",
            "!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            expected = true
        )
    }

    // Permalink: Event ID
    @Test
    fun shouldPassEventIDPermalink() {
        TestHelper.eventId(
            "https://matrix.to/#/!room:example.com/\$event",
            "!room:example.com",
            "\$event",
            expected = true
        )
    }

    @Test
    fun shouldPassEncodedEventIDPermalink() {
        TestHelper.eventId(
            "https://matrix.to/#/!room%3Aexample.com/%24event%3Aexample.org?via=elsewhere.ca",
            "!room:example.com",
            "\$event:example.org",
            expected = true
        )
    }

    @Test
    fun `parses matrixto event v12 links`() {
        TestHelper.eventId(
            "https://matrix.to/#/!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE/\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            "!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            "\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            expected = true
        )
        TestHelper.eventId(
            "https://matrix.to/#/!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE/%24NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            "!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            "\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            expected = true
        )
        TestHelper.eventId(
            "https://matrix.to/#/!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE/\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE?via=example.org&action=join&via=elsewhere.ca",
            "!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            "\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            expected = true
        )
        TestHelper.eventId(
            "https://matrix.to/#%2F!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE%2F%24NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE%3Fvia%3Dexample.org%26action%3Djoin%26via%3Delsewhere.ca",
            "!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            "\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            expected = true
        )
        TestHelper.eventId(
            "https://matrix.to/#/\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            null,
            "\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            expected = true
        )
        TestHelper.eventId(
            "https://matrix.to/#/%24NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            null,
            "\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            expected = true
        )
        TestHelper.eventId(
            "https://matrix.to/#/\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE?via=example.org&action=join&via=elsewhere.ca",
            null,
            "\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            expected = true
        )
        TestHelper.eventId(
            "https://matrix.to/#%2F%24NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE%3Fvia%3Dexample.org%26action%3Djoin%26via%3Delsewhere.ca",
            null,
            "\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            expected = true
        )
    }

    @Test
    fun `ignores overlaps`() {
        val content =
            "lorem @user:example.org ipsum https://matrix.to/#/@user:example.org?action=chat dolor matrix:u/user:example.org sit"
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
                12..22 to Reference.Link("example.org"),
                30..78 to Reference.User(
                    UserId("@user:example.org"),
                    "https://matrix.to/#/@user:example.org?action=chat"
                ),
                86..110 to Reference.User(UserId("@user:example.org"), "matrix:u/user:example.org"),
            ),
            actual = findLinkReferences(content)
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
            actual = findIdReferences(content)
        )
        // Combined
        assertEquals(
            expected = mapOf(
                6..22 to Reference.User(UserId("@user:example.org")),
                30..78 to Reference.User(
                    UserId("@user:example.org"),
                    "https://matrix.to/#/@user:example.org?action=chat"
                ),
                86..110 to Reference.User(UserId("@user:example.org"), "matrix:u/user:example.org"),
            ),
            actual = findReferences(content)
        )
        assertEquals(
            expected = mapOf(
                9..44 to Reference.User(UserId("@user:matrix.org"), "https://matrix.to/#/@user:matrix.org"),
                92..171 to Reference.Room(
                    roomId = RoomId("!WvOltebgJfkgHzhfpW:matrix.org"),
                    uri = "https://matrix.to/#/!WvOltebgJfkgHzhfpW:matrix.org?via=matrix.org&via=imbitbu.de"
                ),
                199..323 to Reference.Event(
                    roomId = RoomId("!WvOltebgJfkgHzhfpW:matrix.org"),
                    eventId = EventId("\$KoEcMwZKqGpCeuMjAmt9zvmWgO72f7hDFkvfBMS479A"),
                    uri = "https://matrix.to/#/!WvOltebgJfkgHzhfpW:matrix.org/\$KoEcMwZKqGpCeuMjAmt9zvmWgO72f7hDFkvfBMS479A?via=matrix.org&via=imbitbu.de"
                ),
            ),
            actual = findReferences(
                "<a href=\"https://matrix.to/#/@user:matrix.org\">Some Username</a>: This is a user mention<br>" +
                        "https://matrix.to/#/!WvOltebgJfkgHzhfpW:matrix.org?via=matrix.org&via=imbitbu.de This is a room mention<br>" +
                        "https://matrix.to/#/!WvOltebgJfkgHzhfpW:matrix.org/\$KoEcMwZKqGpCeuMjAmt9zvmWgO72f7hDFkvfBMS479A?via=matrix.org&via=imbitbu.de This is an event mention"
            )
        )
    }

    @Test
    fun `allows long matrixto links`() {
        val longId = (
                "aaaaaaaaa1aaaaaaaaa2aaaaaaaaa3aaaaaaaaa4aaaaaaaaa5aaaaaaaaa6aaaaaaaaa7aaaaaaaaa8aaaaaaaaa9aaaaaaaa10" +
                        "aaaaaaaa11aaaaaaaa12aaaaaaaa13aaaaaaaa14aaaaaaaa15aaaaaaaa16aaaaaaaa17aaaaaaaa18aaaaaaaa19aaaaaaaa20" +
                        "aaaaaaaa21aaaaaaaa22aaaaaaaa23aaaaaaaa24aa:example.org"
                )
        longId shouldHaveLength 254

        TestHelper.userId(
            "https://matrix.to/#/@$longId",
            "@$longId",
            true
        )
        TestHelper.roomAliasId(
            "https://matrix.to/#/#$longId",
            "#$longId",
            true
        )
        TestHelper.roomId(
            "https://matrix.to/#/!$longId",
            "!$longId",
            true
        )
        TestHelper.eventId(
            "https://matrix.to/#/!$longId/\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            "!$longId",
            "\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            true
        )
        TestHelper.eventId(
            "https://matrix.to/#/!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE/$$longId",
            "!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            "\$$longId",
            true
        )
        TestHelper.eventId(
            "https://matrix.to/#/!$longId/$$longId",
            "!$longId",
            "\$$longId",
            true
        )
        TestHelper.eventId(
            "https://matrix.to/#/$$longId",
            null,
            "\$$longId",
            true
        )
    }

    @Test
    fun `rejects too long matrixto links`() {
        val longId = (
                "aaaaaaaaa1aaaaaaaaa2aaaaaaaaa3aaaaaaaaa4aaaaaaaaa5aaaaaaaaa6aaaaaaaaa7aaaaaaaaa8aaaaaaaaa9aaaaaaaa10" +
                        "aaaaaaaa11aaaaaaaa12aaaaaaaa13aaaaaaaa14aaaaaaaa15aaaaaaaa16aaaaaaaa17aaaaaaaa18aaaaaaaa19aaaaaaaa20" +
                        "aaaaaaaa21aaaaaaaa22aaaaaaaa23aaaaaaaa24aaa:example.org"
                )
        longId shouldHaveLength 255

        TestHelper.userId(
            "https://matrix.to/#/@$longId",
            "@$longId",
            false
        )
        TestHelper.roomAliasId(
            "https://matrix.to/#/#$longId",
            "#$longId",
            false
        )
        TestHelper.roomId(
            "https://matrix.to/#/!$longId",
            "!$longId",
            false
        )
        TestHelper.eventId(
            "https://matrix.to/#/!$longId/\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            "!$longId",
            "\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            false
        )
        TestHelper.roomId( // eventId ignored
            "https://matrix.to/#/!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE/$$longId",
            "!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            true
        )
        TestHelper.eventId(
            "https://matrix.to/#/!$longId/$$longId",
            "!$longId",
            "\$$longId",
            false
        )
        TestHelper.eventId(
            "https://matrix.to/#/$$longId",
            null,
            "\$$longId",
            false
        )
    }

    @Test
    fun `allow long matrix links`() {
        val longId = (
                "aaaaaaaaa1aaaaaaaaa2aaaaaaaaa3aaaaaaaaa4aaaaaaaaa5aaaaaaaaa6aaaaaaaaa7aaaaaaaaa8aaaaaaaaa9aaaaaaaa10" +
                        "aaaaaaaa11aaaaaaaa12aaaaaaaa13aaaaaaaa14aaaaaaaa15aaaaaaaa16aaaaaaaa17aaaaaaaa18aaaaaaaa19aaaaaaaa20" +
                        "aaaaaaaa21aaaaaaaa22aaaaaaaa23aaaaaaaa24aa:example.org"
                )
        longId shouldHaveLength 254

        TestHelper.userId(
            "matrix:u/$longId",
            "@$longId",
            true
        )
        TestHelper.roomAliasId(
            "matrix:r/$longId",
            "#$longId",
            true
        )
        TestHelper.roomId(
            "matrix:roomid/$longId",
            "!$longId",
            true
        )
        TestHelper.eventId(
            "matrix:roomid/$longId/e/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            "!$longId",
            "\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            true
        )
        TestHelper.eventId(
            "matrix:roomid/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE/e/$longId",
            "!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            "\$$longId",
            true
        )
        TestHelper.eventId(
            "matrix:roomid/$longId/e/$longId",
            "!$longId",
            "\$$longId",
            true
        )
    }

    @Test
    fun `rejects too long matrix links`() {
        val longId = (
                "aaaaaaaaa1aaaaaaaaa2aaaaaaaaa3aaaaaaaaa4aaaaaaaaa5aaaaaaaaa6aaaaaaaaa7aaaaaaaaa8aaaaaaaaa9aaaaaaaa10" +
                        "aaaaaaaa11aaaaaaaa12aaaaaaaa13aaaaaaaa14aaaaaaaa15aaaaaaaa16aaaaaaaa17aaaaaaaa18aaaaaaaa19aaaaaaaa20" +
                        "aaaaaaaa21aaaaaaaa22aaaaaaaa23aaaaaaaa24aaa:example.org"
                )
        longId shouldHaveLength 255

        TestHelper.userId(
            "matrix:u/$longId",
            "@$longId",
            false
        )
        TestHelper.roomAliasId(
            "matrix:r/$longId",
            "#$longId",
            false
        )
        TestHelper.roomId(
            "matrix:roomid/$longId",
            "!$longId",
            false
        )
        TestHelper.eventId(
            "matrix:roomid/$longId/e/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            "!$longId",
            "\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            false
        )
        TestHelper.roomId( // ignores event id
            "matrix:roomid/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE/e/$longId",
            "!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE",
            true
        )
        TestHelper.eventId(
            "matrix:roomid/$longId/e/$longId",
            "!$longId",
            "\$$longId",
            false
        )
    }
}

private object TestHelper {
    fun userId(reference: String, expected: Boolean) = userId(reference, reference, expected, true)
    fun userId(reference: String, userId: String, expected: Boolean, isId: Boolean = false) {
        val result = findReferences("prefix $reference suffix")

        if (expected) {
            result shouldContainExactly mapOf(
                7..(7 + reference.lastIndex) to Reference.User(
                    UserId(userId),
                    if (!isId) reference else null
                )
            )
        } else {
            if (isId) result.filterValues { it !is Reference.Link }.shouldBeEmpty()
            else result.shouldBeEmpty()
        }
    }

    fun roomId(reference: String, roomId: String, expected: Boolean) {
        val result = findReferences("prefix $reference suffix")

        if (expected) {
            result shouldContainExactly mapOf(
                7..(7 + reference.lastIndex) to Reference.Room(
                    RoomId(roomId),
                    reference
                )
            )
        } else {
            result.shouldBeEmpty()
        }
    }

    fun roomAliasId(reference: String, expected: Boolean) = roomAliasId(reference, reference, expected, true)

    fun roomAliasId(reference: String, id: String, expected: Boolean, isId: Boolean = false) {
        val result = findReferences("prefix $reference suffix")

        if (expected) {
            result shouldContainExactly mapOf(
                7..(7 + reference.lastIndex) to Reference.RoomAlias(
                    RoomAliasId(id),
                    if (!isId) reference else null
                )
            )
        } else {
            if (isId) result.filterValues { it !is Reference.Link }.shouldBeEmpty()
            else result.shouldBeEmpty()
        }
    }

    fun eventId(reference: String, roomId: String?, eventId: String, expected: Boolean) {
        val result = findReferences("prefix $reference suffix")

        if (expected) {
            result shouldContainExactly mapOf(
                7..(7 + reference.lastIndex) to Reference.Event(
                    roomId?.let(::RoomId),
                    EventId(eventId),
                    reference
                )
            )
        } else {
            result.shouldBeEmpty()
        }
    }

    fun link(reference: String, expected: Boolean) {
        val result = findReferences("prefix $reference suffix")

        if (expected) {
            result shouldContainExactly mapOf(
                7..(7 + reference.lastIndex) to Reference.Link(reference)
            )
        } else {
            result.shouldBeEmpty()
        }
    }
}