package de.connect2x.trixnity.core.util

import io.ktor.http.*
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomAliasId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParseLinkTest {
    @Test
    fun `fails on invalid links`() {
        assertNull(Reference.parseLink("invalid-link"))
        assertNull(Reference.parseLink("192.168.2.24:9090"))
        assertNull(Reference.parseLink("https://example.com"))
        assertNull(Reference.parseLink("https://matrix.to/#/disclaimer/"))
        assertNull(Reference.parseLink("https://matrix.to/robots.txt"))
        assertNull(Reference.parseLink("matrix:group/group:example.com"))
    }

    @Test
    fun `parses matrixto user links`() {
        assertEquals(
            expected = Reference.User(UserId("@user:example.com")),
            actual = Reference.parseLink("https://matrix.to/#/@user:example.com")
        )
        assertEquals(
            expected = Reference.User(UserId("@user:example.com")),
            actual = Reference.parseLink("https://matrix.to/#%2F%40user%3Aexample.com")
        )
        assertEquals(
            expected = Reference.User(UserId("@user:example.com"), parametersOf("action", "chat")),
            actual = Reference.parseLink("https://matrix.to/#/@user:example.com?action=chat")
        )
        assertEquals(
            expected = Reference.User(UserId("@user:example.com"), parametersOf("action", "chat")),
            actual = Reference.parseLink("https://matrix.to/#%2F%40user%3Aexample.com%3Faction=chat")
        )
    }

    @Test
    fun `parses matrix protocol user links`() {
        assertEquals(
            expected = Reference.User(UserId("@user:example.com")),
            actual = Reference.parseLink("matrix:u/user:example.com")
        )
        assertEquals(
            expected = Reference.User(UserId("@user:example.com"), parametersOf("action", "chat")),
            actual = Reference.parseLink("matrix:u/user:example.com?action=chat")
        )
    }

    @Test
    fun `parses matrixto room alias links`() {
        assertEquals(
            expected = Reference.RoomAlias(RoomAliasId("#somewhere:example.org")),
            actual = Reference.parseLink("https://matrix.to/#/#somewhere:example.org")
        )
        assertEquals(
            expected = Reference.RoomAlias(RoomAliasId("#somewhere:example.org")),
            actual = Reference.parseLink("https://matrix.to/#%2F#somewhere%3Aexample.org")
        )
        assertEquals(
            expected = Reference.RoomAlias(RoomAliasId("#somewhere:example.org"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = Reference.parseLink("https://matrix.to/#/#somewhere:example.org?via=example.org&action=join&via=elsewhere.ca")
        )
        assertEquals(
            expected = Reference.RoomAlias(RoomAliasId("#somewhere:example.org"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = Reference.parseLink("https://matrix.to/#%2F#somewhere%3Aexample.org%3Fvia%3Dexample.org%26action%3Djoin%26via%3Delsewhere.ca")
        )
    }

    @Test
    fun `parses matrix protocol room alias links`() {
        assertEquals(
            expected = Reference.RoomAlias(RoomAliasId("#somewhere:example.org")),
            actual = Reference.parseLink("matrix:r/somewhere:example.org")
        )
        assertEquals(
            expected = Reference.RoomAlias(RoomAliasId("#somewhere:example.org"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = Reference.parseLink("matrix:r/somewhere:example.org?via=example.org&action=join&via=elsewhere.ca")
        )
    }

    @Test
    fun `parses matrixto roomid links`() {
        assertEquals(
            expected = Reference.Room(RoomId("!somewhere:example.org")),
            actual = Reference.parseLink("https://matrix.to/#/!somewhere:example.org")
        )
        assertEquals(
            expected = Reference.Room(RoomId("!somewhere:example.org")),
            actual = Reference.parseLink("https://matrix.to/#%2F!somewhere%3Aexample.org")
        )
        assertEquals(
            expected = Reference.Room(RoomId("!somewhere:example.org"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = Reference.parseLink("https://matrix.to/#/!somewhere:example.org?via=example.org&action=join&via=elsewhere.ca")
        )
        assertEquals(
            expected = Reference.Room(RoomId("!somewhere:example.org"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = Reference.parseLink("https://matrix.to/#%2F!somewhere%3Aexample.org%3Fvia%3Dexample.org%26action%3Djoin%26via%3Delsewhere.ca")
        )
    }

    @Test
    fun `parses matrix protocol roomid links`() {
        assertEquals(
            expected = Reference.Room(RoomId("!somewhere:example.org")),
            actual = Reference.parseLink("matrix:roomid/somewhere:example.org")
        )
        assertEquals(
            expected = Reference.Room(RoomId("!somewhere:example.org"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = Reference.parseLink("matrix:roomid/somewhere:example.org?via=example.org&action=join&via=elsewhere.ca")
        )
    }

    @Test
    fun `parses matrixto roomid v12 links`() {
        assertEquals(
            expected = Reference.Room(RoomId("!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = Reference.parseLink("https://matrix.to/#/!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Reference.Room(RoomId("!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = Reference.parseLink("https://matrix.to/#%2F!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Reference.Room(RoomId("!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = Reference.parseLink("https://matrix.to/#/!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE?via=example.org&action=join&via=elsewhere.ca")
        )
        assertEquals(
            expected = Reference.Room(RoomId("!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = Reference.parseLink("https://matrix.to/#%2F!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE%3Fvia%3Dexample.org%26action%3Djoin%26via%3Delsewhere.ca")
        )
    }

    @Test
    fun `parses matrix protocol roomid v12 links`() {
        assertEquals(
            expected = Reference.Room(RoomId("!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = Reference.parseLink("matrix:roomid/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Reference.Room(RoomId("!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = Reference.parseLink("matrix:roomid/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE?via=example.org&action=join&via=elsewhere.ca")
        )
    }

    @Test
    fun `parses matrixto event links`() {
        assertEquals(
            expected = Reference.Event(RoomId("!somewhere:example.org"), EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = Reference.parseLink("https://matrix.to/#/!somewhere:example.org/\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Reference.Event(RoomId("!somewhere:example.org"), EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = Reference.parseLink("https://matrix.to/#/!somewhere%3Aexample.org/%24NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Reference.Event(RoomId("!somewhere:example.org"),EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"), parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = Reference.parseLink("https://matrix.to/#/!somewhere:example.org/\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE?via=example.org&action=join&via=elsewhere.ca")
        )
        assertEquals(
            expected = Reference.Event(RoomId("!somewhere:example.org"),EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"), parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = Reference.parseLink("https://matrix.to/#%2F!somewhere%3Aexample.org%2F%24NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE%3Fvia%3Dexample.org%26action%3Djoin%26via%3Delsewhere.ca")
        )
        assertEquals(
            expected = Reference.Event(null, EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = Reference.parseLink("https://matrix.to/#/\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Reference.Event(null, EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = Reference.parseLink("https://matrix.to/#/%24NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Reference.Event(null, EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = Reference.parseLink("https://matrix.to/#/\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE?via=example.org&action=join&via=elsewhere.ca")
        )
        assertEquals(
            expected = Reference.Event(null, EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = Reference.parseLink("https://matrix.to/#%2F%24NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE%3Fvia%3Dexample.org%26action%3Djoin%26via%3Delsewhere.ca")
        )
    }

    @Test
    fun `parses matrix protocol event links`() {
        assertEquals(
            expected = Reference.Event(RoomId("!somewhere:example.org"), EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = Reference.parseLink("matrix:roomid/somewhere:example.org/e/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Reference.Event(RoomId("!somewhere:example.org"), EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = Reference.parseLink("matrix:roomid/somewhere:example.org/e/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE?via=example.org&action=join&via=elsewhere.ca")
        )
    }

    @Test
    fun `parses matrixto event v12 links`() {
        assertEquals(
            expected = Reference.Event(RoomId("!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"), EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = Reference.parseLink("https://matrix.to/#/!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE/\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Reference.Event(RoomId("!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"), EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = Reference.parseLink("https://matrix.to/#/!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE/%24NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Reference.Event(RoomId("!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"), EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"), parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = Reference.parseLink("https://matrix.to/#/!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE/\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE?via=example.org&action=join&via=elsewhere.ca")
        )
        assertEquals(
            expected = Reference.Event(RoomId("!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"), EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"), parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = Reference.parseLink("https://matrix.to/#%2F!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE%2F%24NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE%3Fvia%3Dexample.org%26action%3Djoin%26via%3Delsewhere.ca")
        )
        assertEquals(
            expected = Reference.Event(null, EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = Reference.parseLink("https://matrix.to/#/\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Reference.Event(null, EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = Reference.parseLink("https://matrix.to/#/%24NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Reference.Event(null, EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = Reference.parseLink("https://matrix.to/#/\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE?via=example.org&action=join&via=elsewhere.ca")
        )
        assertEquals(
            expected = Reference.Event(null, EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = Reference.parseLink("https://matrix.to/#%2F%24NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE%3Fvia%3Dexample.org%26action%3Djoin%26via%3Delsewhere.ca")
        )
    }

    @Test
    fun `parses matrix protocol event v12 links`() {
        assertEquals(
            expected = Reference.Event(RoomId("!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"), EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = Reference.parseLink("matrix:roomid/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE/e/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Reference.Event(RoomId("!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"), EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = Reference.parseLink("matrix:roomid/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE/e/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE?via=example.org&action=join&via=elsewhere.ca")
        )
    }

    @Test
    fun `allows long matrixto links`() {
        val longId = (
                "aaaaaaaaa1aaaaaaaaa2aaaaaaaaa3aaaaaaaaa4aaaaaaaaa5aaaaaaaaa6aaaaaaaaa7aaaaaaaaa8aaaaaaaaa9aaaaaaaa10" +
                        "aaaaaaaa11aaaaaaaa12aaaaaaaa13aaaaaaaa14aaaaaaaa15aaaaaaaa16aaaaaaaa17aaaaaaaa18aaaaaaaa19aaaaaaaa20" +
                        "aaaaaaaa21aaaaaaaa22aaaaaaaa23aaaaaaaa24aa:example.org"
                )
        assertEquals(
            expected = 254,
            actual = longId.length,
        )
        assertEquals(
            expected = Reference.User(UserId(UserId.sigilCharacter + longId)),
            actual = Reference.parseLink("https://matrix.to/#/@$longId")
        )
        assertEquals(
            expected = Reference.RoomAlias(RoomAliasId(RoomAliasId.sigilCharacter + longId)),
            actual = Reference.parseLink("https://matrix.to/#/#$longId")
        )
        assertEquals(
            expected = Reference.Room(RoomId(RoomId.sigilCharacter + longId)),
            actual = Reference.parseLink("https://matrix.to/#/!$longId")
        )
        assertEquals(
            expected = Reference.Event(RoomId(RoomId.sigilCharacter + longId), EventId(EventId.sigilCharacter + "NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = Reference.parseLink("https://matrix.to/#/!$longId/\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Reference.Event(RoomId(RoomId.sigilCharacter + "NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"), EventId(EventId.sigilCharacter + longId)),
            actual = Reference.parseLink("https://matrix.to/#/!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE/$$longId")
        )
        assertEquals(
            expected = Reference.Event(RoomId(RoomId.sigilCharacter + longId), EventId(EventId.sigilCharacter + longId)),
            actual = Reference.parseLink("https://matrix.to/#/!$longId/$$longId")
        )
        assertEquals(
            expected = Reference.Event(null, EventId(EventId.sigilCharacter + longId)),
            actual = Reference.parseLink("https://matrix.to/#/$$longId")
        )
    }

    @Test
    fun `allows long matrix protocol links`() {
        val longId = (
                "aaaaaaaaa1aaaaaaaaa2aaaaaaaaa3aaaaaaaaa4aaaaaaaaa5aaaaaaaaa6aaaaaaaaa7aaaaaaaaa8aaaaaaaaa9aaaaaaaa10" +
                        "aaaaaaaa11aaaaaaaa12aaaaaaaa13aaaaaaaa14aaaaaaaa15aaaaaaaa16aaaaaaaa17aaaaaaaa18aaaaaaaa19aaaaaaaa20" +
                        "aaaaaaaa21aaaaaaaa22aaaaaaaa23aaaaaaaa24aa:example.org"
                )
        assertEquals(
            expected = 254,
            actual = longId.length,
        )
        assertEquals(
            expected = Reference.User(UserId(UserId.sigilCharacter + longId)),
            actual = Reference.parseLink("matrix:u/$longId")
        )
        assertEquals(
            expected = Reference.RoomAlias(RoomAliasId(RoomAliasId.sigilCharacter + longId)),
            actual = Reference.parseLink("matrix:r/$longId")
        )
        assertEquals(
            expected = Reference.Room(RoomId(RoomId.sigilCharacter + longId)),
            actual = Reference.parseLink("matrix:roomid/$longId")
        )
        assertEquals(
            expected = Reference.Event(RoomId(RoomId.sigilCharacter + longId), EventId(EventId.sigilCharacter + "NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = Reference.parseLink("matrix:roomid/$longId/e/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Reference.Event(RoomId(RoomId.sigilCharacter + "NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"), EventId(EventId.sigilCharacter + longId)),
            actual = Reference.parseLink("matrix:roomid/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE/e/$longId")
        )
        assertEquals(
            expected = Reference.Event(RoomId(RoomId.sigilCharacter + longId), EventId(EventId.sigilCharacter + longId)),
            actual = Reference.parseLink("matrix:roomid/$longId/e/$longId")
        )
    }

    @Test
    fun `rejects too long matrixto links`() {
        val tooLongId = (
                "aaaaaaaaa1aaaaaaaaa2aaaaaaaaa3aaaaaaaaa4aaaaaaaaa5aaaaaaaaa6aaaaaaaaa7aaaaaaaaa8aaaaaaaaa9aaaaaaaa10" +
                        "aaaaaaaa11aaaaaaaa12aaaaaaaa13aaaaaaaa14aaaaaaaa15aaaaaaaa16aaaaaaaa17aaaaaaaa18aaaaaaaa19aaaaaaaa20" +
                        "aaaaaaaa21aaaaaaaa22aaaaaaaa23aaaaaaaa24aaaaaaaa25aaaaaaaa26:example.org"
                )
        assertEquals(
            expected = null,
            actual = Reference.parseLink("https://matrix.to/#/@$tooLongId")
        )
        assertEquals(
            expected = null,
            actual = Reference.parseLink("https://matrix.to/#/#$tooLongId")
        )
        assertEquals(
            expected = null,
            actual = Reference.parseLink("https://matrix.to/#/!$tooLongId")
        )
        assertEquals(
            expected = null,
            actual = Reference.parseLink("https://matrix.to/#/!$tooLongId/\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = null,
            actual = Reference.parseLink("https://matrix.to/#/!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE/e/$$tooLongId")
        )
        assertEquals(
            expected = null,
            actual = Reference.parseLink("https://matrix.to/#/!$tooLongId/$$tooLongId")
        )
        assertEquals(
            expected = null,
            actual = Reference.parseLink("https://matrix.to/#/$$tooLongId")
        )
    }

    @Test
    fun `rejects too long matrix protocol links`() {
        val tooLongId = (
                "aaaaaaaaa1aaaaaaaaa2aaaaaaaaa3aaaaaaaaa4aaaaaaaaa5aaaaaaaaa6aaaaaaaaa7aaaaaaaaa8aaaaaaaaa9aaaaaaaa10" +
                        "aaaaaaaa11aaaaaaaa12aaaaaaaa13aaaaaaaa14aaaaaaaa15aaaaaaaa16aaaaaaaa17aaaaaaaa18aaaaaaaa19aaaaaaaa20" +
                        "aaaaaaaa21aaaaaaaa22aaaaaaaa23aaaaaaaa24aaaaaaaa25aaaaaaaa26:example.org"
                )
        assertEquals(
            expected = null,
            actual = Reference.parseLink("matrix:u/$tooLongId")
        )
        assertEquals(
            expected = null,
            actual = Reference.parseLink("matrix:r/$tooLongId")
        )
        assertEquals(
            expected = null,
            actual = Reference.parseLink("matrix:roomid/$tooLongId")
        )
        assertEquals(
            expected = null,
            actual = Reference.parseLink("matrix:roomid/$tooLongId/e/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = null,
            actual = Reference.parseLink("matrix:roomid/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE/e/$tooLongId")
        )
        assertEquals(
            expected = null,
            actual = Reference.parseLink("matrix:roomid/$tooLongId/e/$tooLongId")
        )
        assertEquals(
            expected = null,
            actual = Reference.parseLink("matrix:e/$tooLongId")
        )
    }
}
