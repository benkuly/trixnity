package net.folivo.trixnity.core

import io.kotest.matchers.shouldBe
import net.folivo.trixnity.core.MatrixRegex.findMentions
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import kotlin.test.Test


class MatrixRegexTest {
    fun positiveUserIdTest(id: String, localpart: String, domain: String, matcher: Regex? = null) {
        val message = "Hello $id"

        val result =
            if (matcher != null) findMentions(message, matcher)
            else findMentions(message)
        result.size shouldBe 1
        result[id] shouldBe UserId(localpart, domain)
    }

    fun positiveRoomIdTest(id: String, localpart: String, domain: String, matcher: Regex? = null) {
        val message = "omw to $id"

        val result =
            if (matcher != null) findMentions(message, matcher)
            else findMentions(message)
        result.size shouldBe 1
        result[id] shouldBe RoomId(localpart, domain)
    }

    fun positiveRoomAliasTest(id: String, localpart: String, domain: String, matcher: Regex? = null) {
        val message = "omw to $id"

        val result =
            if (matcher != null) findMentions(message, matcher)
            else findMentions(message)
        result.size shouldBe 1
        result[id] shouldBe RoomAliasId(localpart, domain)
    }

    fun positiveEventIdTest(id: String, opaqueId: String, room: String = "", matcher: Regex? = null) {
        val message = "participating at $id"

        val result =
            if (matcher != null) findMentions(message, matcher)
            else findMentions(message)
        result.size shouldBe 1
        result[id] shouldBe EventId(opaqueId, RoomAliasId(room))
    }

    fun negativeTest(id: String, matcher: Regex? = null) {
        val message = "Hello $id"

        val result =
            if (matcher != null) findMentions(message, matcher)
            else findMentions(message)
        println(result)

        result.size shouldBe 0
    }

    // Users
    @Test
    fun matchValidUserIdentifier() {
        positiveUserIdTest("@a9._=-/+:example.com", "a9._=-/+", "example.com")
    }

    @Test
    fun matchValidUserIdentifierWithMatrixToLink() {
        positiveUserIdTest("<a href=\"https://matrix.to/#/@user:example.com\">Hallo</a>", "user", "example.com")
    }

    @Test
    fun matchValidUserIdentifierWithMatrixToLinkWithoutHref() {
        positiveUserIdTest("https://matrix.to/#/@user:example.com", "user", "example.com")
    }

    @Test
    fun matchValidUserIdentifierWithMatrixULinkAndActionAttribute() {
        positiveUserIdTest("matrix:u/user:example.com?action=chat", "user", "example.com")
    }

    @Test
    fun matchValidUserIdentifierWithMatrixULinkAndViaAttribute() {
        positiveUserIdTest("matrix:u/user:example.com?via=example.com", "user", "example.com")
    }

    @Test
    fun matchValidUserIdentifierWithMatrixULinkViaAndActionAttribute() {
        positiveUserIdTest("matrix:u/user:example.com?via=example.com&action=chat", "user", "example.com")
    }

    fun matchValidUserIdentifierWithMatrixULinkActionAndViaAttribute() {
        positiveUserIdTest("matrix:u/user:example.com?action=chat&via=example.com", "user", "example.com")
    }

    @Test
    fun matchValidUserIdentifierWithMatrixULink() {
        positiveUserIdTest("matrix:u/user:example.com", "user", "example.com")
    }

    @Test
    fun matchValidUserIdentifierWithSpecialCharacters() {
        positiveUserIdTest("@user:sub.example.com:8000", "user", "sub.example.com:8000")
    }

    @Test
    fun matchValidUserIdentifierWithIPV4() {
        positiveUserIdTest("@user:1.1.1.1", "user", "1.1.1.1")
    }

    @Test
    fun matchValidUserIdentifierWithIPV6() {
        positiveUserIdTest(
            "@user:[2001:0db8:85a3:0000:0000:8a2e:0370:7334]",
            "user",
            "[2001:0db8:85a3:0000:0000:8a2e:0370:7334]"
        )
    }

    @Test
    fun notMatchInvalidLocalpart() {
        negativeTest("@user&:example.com")
    }

    @Test
    fun notMatchInvalidDomain() {
        negativeTest("@user:ex&mple.com")
    }

    @Test
    fun notMatchInvalidIPV4WithCharacters() {
        negativeTest("1.1.1.Abc", MatrixRegex.IPv4)
    }

    @Test
    fun notMatchInvalidIPV6WithIllegalCharacters() {
        negativeTest("@user:[2001:8a2e:0370:733G]")
    }

    @Test
    fun notMatchIncompleteHtmlTag() {
        negativeTest("""<a href="https://matrix.to/#/@user:example.com"""", MatrixRegex.userHtmlAnchor)
    }

    @Test
    fun notMatchInvalidHtmlLinkTag() {
        negativeTest("<b href=\"https://matrix.to/#/@user:example.com>User</b>", MatrixRegex.userHtmlAnchor)
    }

    // Room Alias
    @Test
    fun matchValidRoomAlias() {
        positiveRoomAliasTest("#a9._=-/+:example.com", "a9._=-/+", "example.com")
    }

    @Test
    fun matchValidRoomAliasWithMatrixToLink() {
        positiveRoomAliasTest("<a href=\"https://matrix.to/#/#user:example.com\">Hallo</a>", "user", "example.com")
    }

    @Test
    fun matchValidRoomAliasWithMatrixToLinkWithoutHref() {
        positiveRoomAliasTest("https://matrix.to/#/#user:example.com", "user", "example.com")
    }

    @Test
    fun matchValidRoomAliasWithMatrixULinkAndActionAttribute() {
        positiveRoomAliasTest("matrix:r/user:example.com?action=join", "user", "example.com")
    }

    @Test
    fun matchValidRoomAliasWithMatrixULinkAndViaAttribute() {
        positiveRoomAliasTest("matrix:r/user:example.com?via=example.com", "user", "example.com")
    }

    @Test
    fun matchValidRoomAliasWithMatrixULinkViaAndActionAttribute() {
        positiveRoomAliasTest("matrix:r/user:example.com?via=example.com&action=join", "user", "example.com")
    }

    fun matchValidRoomAliasWithMatrixULinkActionAndViaAttribute() {
        positiveRoomAliasTest("matrix:r/user:example.com?action=chat&via=example.com", "user", "example.com")
    }

    @Test
    fun matchValidRoomAliasWithMatrixULink() {
        positiveRoomAliasTest("matrix:r/user:example.com", "user", "example.com")
    }

    @Test
    fun matchValidRoomAliasWithSpecialCharacters() {
        positiveRoomAliasTest("#user:sub.example.com:8000", "user", "sub.example.com:8000")
    }

    @Test
    fun matchValidRoomAliasWithIPV4() {
        positiveRoomAliasTest("#user:1.1.1.1", "user", "1.1.1.1")
    }

    @Test
    fun matchValidRoomAliasWithIPV6() {
        positiveRoomAliasTest(
            "#user:[2001:0db8:85a3:0000:0000:8a2e:0370:7334]",
            "user",
            "[2001:0db8:85a3:0000:0000:8a2e:0370:7334]"
        )
    }

    // Room IDs
    @Test
    fun matchValidRoomIdentifier() {
        positiveRoomIdTest("!a9._=-/+:example.com", "a9._=-/+", "example.com")
    }

    @Test
    fun matchValidRoomIdentifierWithMatrixToLink() {
        positiveRoomIdTest("<a href=\"https://matrix.to/#/!user:example.com\">Hallo</a>", "user", "example.com")
    }

    @Test
    fun matchValidRoomIdentifierWithMatrixToLinkWithoutHref() {
        positiveRoomIdTest("https://matrix.to/#/!user:example.com", "user", "example.com")
    }

    @Test
    fun matchValidRoomIdentifierWithMatrixULinkAndActionAttribute() {
        positiveRoomIdTest("matrix:roomid/user:example.com?action=join", "user", "example.com")
    }

    @Test
    fun matchValidRoomIdentifierWithMatrixULinkAndViaAttribute() {
        positiveRoomIdTest("matrix:roomid/user:example.com?via=example.com", "user", "example.com")
    }

    @Test
    fun matchValidRoomIdentifierWithMatrixULinkViaAndActionAttribute() {
        positiveRoomIdTest("matrix:roomid/user:example.com?via=example.com&action=join", "user", "example.com")
    }

    fun matchValidRoomIdentifierWithMatrixULinkActionAndViaAttribute() {
        positiveRoomIdTest("matrix:roomid/user:example.com?action=chat&via=example.com", "user", "example.com")
    }

    @Test
    fun matchValidRoomIdentifierWithMatrixULink() {
        positiveRoomIdTest("matrix:roomid/user:example.com", "user", "example.com")
    }

    @Test
    fun matchValidRoomIdentifierWithSpecialCharacters() {
        positiveRoomIdTest("!user:sub.example.com:8000", "user", "sub.example.com:8000")
    }

    @Test
    fun matchValidRoomIdentifierWithIPV4() {
        positiveRoomIdTest("!user:1.1.1.1", "user", "1.1.1.1")
    }

    @Test
    fun matchValidRoomIdentifierWithIPV6() {
        positiveRoomIdTest(
            "!user:[2001:0db8:85a3:0000:0000:8a2e:0370:7334]",
            "user",
            "[2001:0db8:85a3:0000:0000:8a2e:0370:7334]"
        )
    }

    // Event IDs
    @Test
    fun matchValidEventIdentifier() {
        positiveEventIdTest("\$event", "event")
    }

    @Test
    fun matchValidEventIdentifierWithMatrixToLink() {
        positiveEventIdTest("<a href=\"https://matrix.to/#/!user:example.com/\$event\">Hallo</a>", "event",  "!user:example.com")
    }

    @Test
    fun matchValidEventIdentifierWithMatrixToLinkWithoutHref() {
        positiveEventIdTest("https://matrix.to/#/!user:example.com/\$event", "event", "!user:example.com")
    }

    @Test
    fun matchValidEventIdentifierWithMatrixULinkAndActionAttribute() {
        positiveEventIdTest("matrix:roomid/user:example.com/e/event?action=join", "event", "!user:example.com")
    }

    @Test
    fun matchValidEventIdentifierWithMatrixULinkAndViaAttribute() {
        positiveEventIdTest("matrix:roomid/user:example.com/e/event?via=example.com", "event", "!user:example.com")
    }

    @Test
    fun matchValidEventIdentifierWithMatrixULinkViaAndActionAttribute() {
        positiveEventIdTest("matrix:roomid/user:example.com/e/event?via=example.com&action=join", "event", "!user:example.com")
    }

    fun matchValidEventIdentifierWithMatrixULinkActionAndViaAttribute() {
        positiveEventIdTest("matrix:roomid/user:example.com/e/event?action=chat&via=example.com", "event", "!user:example.com")
    }

    @Test
    fun matchValidEventIdentifierWithMatrixULink() {
        positiveEventIdTest("matrix:roomid/user:example.com/e/event", "event", "!user:example.com")
    }
}
