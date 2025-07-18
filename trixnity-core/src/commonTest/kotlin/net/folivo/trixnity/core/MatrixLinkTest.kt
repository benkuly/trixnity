package net.folivo.trixnity.core

import io.ktor.http.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.Mention
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.util.MatrixLinks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatrixLinkTest {
    @Test
    fun `fails on invalid links`() {
        assertNull(MatrixLinks.parse("invalid-link"))
        assertNull(MatrixLinks.parse("https://example.com"))
        assertNull(MatrixLinks.parse("https://matrix.to/#/disclaimer/"))
        assertNull(MatrixLinks.parse("https://matrix.to/robots.txt"))
        assertNull(MatrixLinks.parse("matrix:group/group:example.com"))
    }

    @Test
    fun `parses matrixto user links`() {
        assertEquals(
            expected = Mention.User(UserId("@user:example.com")),
            actual = MatrixLinks.parse("https://matrix.to/#/@user:example.com")
        )
        assertEquals(
            expected = Mention.User(UserId("@user:example.com")),
            actual = MatrixLinks.parse("https://matrix.to/#%2F%40user%3Aexample.com")
        )
        assertEquals(
            expected = Mention.User(UserId("@user:example.com"), parametersOf("action", "chat")),
            actual = MatrixLinks.parse("https://matrix.to/#/@user:example.com?action=chat")
        )
        assertEquals(
            expected = Mention.User(UserId("@user:example.com"), parametersOf("action", "chat")),
            actual = MatrixLinks.parse("https://matrix.to/#%2F%40user%3Aexample.com%3Faction=chat")
        )
    }

    @Test
    fun `parses matrix protocol user links`() {
        assertEquals(
            expected = Mention.User(UserId("@user:example.com")),
            actual = MatrixLinks.parse("matrix:u/user:example.com")
        )
        assertEquals(
            expected = Mention.User(UserId("@user:example.com"), parametersOf("action", "chat")),
            actual = MatrixLinks.parse("matrix:u/user:example.com?action=chat")
        )
    }

    @Test
    fun `parses matrixto room alias links`() {
        assertEquals(
            expected = Mention.RoomAlias(RoomAliasId("#somewhere:example.org")),
            actual = MatrixLinks.parse("https://matrix.to/#/#somewhere:example.org")
        )
        assertEquals(
            expected = Mention.RoomAlias(RoomAliasId("#somewhere:example.org")),
            actual = MatrixLinks.parse("https://matrix.to/#%2F#somewhere%3Aexample.org")
        )
        assertEquals(
            expected = Mention.RoomAlias(RoomAliasId("#somewhere:example.org"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = MatrixLinks.parse("https://matrix.to/#/#somewhere:example.org?via=example.org&action=join&via=elsewhere.ca")
        )
        assertEquals(
            expected = Mention.RoomAlias(RoomAliasId("#somewhere:example.org"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = MatrixLinks.parse("https://matrix.to/#%2F#somewhere%3Aexample.org%3Fvia%3Dexample.org%26action%3Djoin%26via%3Delsewhere.ca")
        )
    }

    @Test
    fun `parses matrix protocol room alias links`() {
        assertEquals(
            expected = Mention.RoomAlias(RoomAliasId("#somewhere:example.org")),
            actual = MatrixLinks.parse("matrix:r/somewhere:example.org")
        )
        assertEquals(
            expected = Mention.RoomAlias(RoomAliasId("#somewhere:example.org"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = MatrixLinks.parse("matrix:r/somewhere:example.org?via=example.org&action=join&via=elsewhere.ca")
        )
    }

    @Test
    fun `parses matrixto roomid links`() {
        assertEquals(
            expected = Mention.Room(RoomId("!somewhere:example.org")),
            actual = MatrixLinks.parse("https://matrix.to/#/!somewhere:example.org")
        )
        assertEquals(
            expected = Mention.Room(RoomId("!somewhere:example.org")),
            actual = MatrixLinks.parse("https://matrix.to/#%2F!somewhere%3Aexample.org")
        )
        assertEquals(
            expected = Mention.Room(RoomId("!somewhere:example.org"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = MatrixLinks.parse("https://matrix.to/#/!somewhere:example.org?via=example.org&action=join&via=elsewhere.ca")
        )
        assertEquals(
            expected = Mention.Room(RoomId("!somewhere:example.org"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = MatrixLinks.parse("https://matrix.to/#%2F!somewhere%3Aexample.org%3Fvia%3Dexample.org%26action%3Djoin%26via%3Delsewhere.ca")
        )
    }

    @Test
    fun `parses matrix protocol roomid links`() {
        assertEquals(
            expected = Mention.Room(RoomId("!somewhere:example.org")),
            actual = MatrixLinks.parse("matrix:roomid/somewhere:example.org")
        )
        assertEquals(
            expected = Mention.Room(RoomId("!somewhere:example.org"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = MatrixLinks.parse("matrix:roomid/somewhere:example.org?via=example.org&action=join&via=elsewhere.ca")
        )
    }

    @Test
    fun `parses matrixto roomid (v12) links`() {
        assertEquals(
            expected = Mention.Room(RoomId("!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = MatrixLinks.parse("https://matrix.to/#/!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Mention.Room(RoomId("!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = MatrixLinks.parse("https://matrix.to/#%2F!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Mention.Room(RoomId("!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = MatrixLinks.parse("https://matrix.to/#/!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE?via=example.org&action=join&via=elsewhere.ca")
        )
        assertEquals(
            expected = Mention.Room(RoomId("!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = MatrixLinks.parse("https://matrix.to/#%2F!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE%3Fvia%3Dexample.org%26action%3Djoin%26via%3Delsewhere.ca")
        )
    }

    @Test
    fun `parses matrix protocol roomid (v12) links`() {
        assertEquals(
            expected = Mention.Room(RoomId("!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = MatrixLinks.parse("matrix:roomid/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Mention.Room(RoomId("!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = MatrixLinks.parse("matrix:roomid/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE?via=example.org&action=join&via=elsewhere.ca")
        )
    }

    @Test
    fun `parses matrixto event links`() {
        assertEquals(
            expected = Mention.Event(RoomId("!somewhere:example.org"), EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = MatrixLinks.parse("https://matrix.to/#/!somewhere:example.org/\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Mention.Event(RoomId("!somewhere:example.org"), EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = MatrixLinks.parse("https://matrix.to/#/!somewhere%3Aexample.org/%24NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Mention.Event(RoomId("!somewhere:example.org"),EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"), parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = MatrixLinks.parse("https://matrix.to/#/!somewhere:example.org/\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE?via=example.org&action=join&via=elsewhere.ca")
        )
        assertEquals(
            expected = Mention.Event(RoomId("!somewhere:example.org"),EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"), parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = MatrixLinks.parse("https://matrix.to/#%2F!somewhere%3Aexample.org%2F%24NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE%3Fvia%3Dexample.org%26action%3Djoin%26via%3Delsewhere.ca")
        )
        assertEquals(
            expected = Mention.Event(null, EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = MatrixLinks.parse("https://matrix.to/#/\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Mention.Event(null, EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = MatrixLinks.parse("https://matrix.to/#/%24NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Mention.Event(null, EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = MatrixLinks.parse("https://matrix.to/#/\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE?via=example.org&action=join&via=elsewhere.ca")
        )
        assertEquals(
            expected = Mention.Event(null, EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = MatrixLinks.parse("https://matrix.to/#%2F%24NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE%3Fvia%3Dexample.org%26action%3Djoin%26via%3Delsewhere.ca")
        )
    }

    @Test
    fun `parses matrix protocol event links`() {
        assertEquals(
            expected = Mention.Event(RoomId("!somewhere:example.org"), EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = MatrixLinks.parse("matrix:roomid/somewhere:example.org/e/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Mention.Event(RoomId("!somewhere:example.org"), EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = MatrixLinks.parse("matrix:roomid/somewhere:example.org/e/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE?via=example.org&action=join&via=elsewhere.ca")
        )
    }

    @Test
    fun `parses matrixto event (v12) links`() {
        assertEquals(
            expected = Mention.Event(RoomId("!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"), EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = MatrixLinks.parse("https://matrix.to/#/!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE/\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Mention.Event(RoomId("!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"), EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = MatrixLinks.parse("https://matrix.to/#/!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE/%24NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Mention.Event(RoomId("!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"), EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"), parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = MatrixLinks.parse("https://matrix.to/#/!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE/\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE?via=example.org&action=join&via=elsewhere.ca")
        )
        assertEquals(
            expected = Mention.Event(RoomId("!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"), EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"), parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = MatrixLinks.parse("https://matrix.to/#%2F!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE%2F%24NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE%3Fvia%3Dexample.org%26action%3Djoin%26via%3Delsewhere.ca")
        )
        assertEquals(
            expected = Mention.Event(null, EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = MatrixLinks.parse("https://matrix.to/#/\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Mention.Event(null, EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = MatrixLinks.parse("https://matrix.to/#/%24NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Mention.Event(null, EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = MatrixLinks.parse("https://matrix.to/#/\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE?via=example.org&action=join&via=elsewhere.ca")
        )
        assertEquals(
            expected = Mention.Event(null, EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = MatrixLinks.parse("https://matrix.to/#%2F%24NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE%3Fvia%3Dexample.org%26action%3Djoin%26via%3Delsewhere.ca")
        )
    }

    @Test
    fun `parses matrix protocol event (v12) links`() {
        assertEquals(
            expected = Mention.Event(RoomId("!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"), EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")),
            actual = MatrixLinks.parse("matrix:roomid/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE/e/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE")
        )
        assertEquals(
            expected = Mention.Event(RoomId("!NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"), EventId("\$NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE"),parametersOf(
                "action" to listOf("join"),
                "via" to listOf("example.org", "elsewhere.ca")
            )),
            actual = MatrixLinks.parse("matrix:roomid/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE/e/NXTQJLZfL7TpVrS6TcznngpZiiuwZcJXdr1ODlnT-sE?via=example.org&action=join&via=elsewhere.ca")
        )
    }
}
