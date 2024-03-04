package net.folivo.trixnity.core

import io.kotest.matchers.shouldBe
import net.folivo.trixnity.core.MatrixRegex.findMentions
import net.folivo.trixnity.core.model.*
import kotlin.test.Test


class MatrixRegexTest {
    fun positiveUserIdTest(id: String, localpart: String, domain: String, matcher: Regex? = null) {
        val message = "Hello $id"

        val result =
            if (matcher != null) findMentions(message, matcher)
            else findMentions(message)
        result.size shouldBe 1
        (result[id] as Mention.User).userId shouldBe UserId(localpart, domain)
    }

    fun positiveRoomIdTest(id: String, localpart: String, domain: String, matcher: Regex? = null) {
        val message = "omw to $id"

        val result =
            if (matcher != null) findMentions(message, matcher)
            else findMentions(message)
        result.size shouldBe 1
        (result[id] as Mention.Room).roomId shouldBe RoomId(localpart, domain)
    }

    fun positiveRoomAliasTest(id: String, localpart: String, domain: String, matcher: Regex? = null) {
        val message = "omw to $id"

        val result =
            if (matcher != null) findMentions(message, matcher)
            else findMentions(message)
        result.size shouldBe 1
        (result[id] as Mention.RoomAlias).roomAliasId shouldBe RoomAliasId(localpart, domain)
    }

    fun positiveEventIdTest(id: String, opaqueId: String, matcher: Regex? = null) {
        val message = "participating at $id"

        val result =
            if (matcher != null) findMentions(message, matcher)
            else findMentions(message)
        result.size shouldBe 1
        (result[id] as Mention.Event).eventId shouldBe EventId(opaqueId)
    }

    fun negativeTest(id: String, matcher: Regex? = null) {
        val message = "Hello $id"

        val result =
            if (matcher != null) findMentions(message, matcher)
            else findMentions(message)
        println(result)

        result.size shouldBe 0
    }

    // General
    @Test
    fun notMatchInvalidIPV4WithCharacters() {
        negativeTest("1.1.1.Abc", MatrixRegex.IPv4)
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
    fun notMatchInvalidUserLocalpart() {
        negativeTest("@user&:example.com")
    }

    @Test
    fun notMatchInvalidUserDomain() {
        negativeTest("@user:ex&mple.com")
    }

    @Test
    fun notMatchInvalidUserIPV6WithIllegalCharacters() {
        negativeTest("@user:[2001:8a2e:0370:733G]")
    }

    @Test
    fun notMatchIncompleteUserHtmlTag() {
        negativeTest("""<a href="https://matrix.to/#/@user:example.com"""", MatrixRegex.userHtmlAnchor)
    }

    @Test
    fun notMatchInvalidUserHtmlLinkTag() {
        negativeTest("<b href=\"https://matrix.to/#/@user:example.com>User</b>", MatrixRegex.userHtmlAnchor)
    }

    // Room Alias
    @Test
    fun matchValidRoomAlias() {
        positiveRoomAliasTest("#a9._=-/+:example.com", "a9._=-/+", "example.com")
    }

    @Test
    fun matchValidRoomAliasWithMatrixToLink() {
        positiveRoomAliasTest("<a href=\"https://matrix.to/#/#room:example.com\">Hallo</a>", "room", "example.com")
    }

    @Test
    fun matchValidRoomAliasWithMatrixToLinkWithoutHref() {
        positiveRoomAliasTest("https://matrix.to/#/#room:example.com", "room", "example.com")
    }

    @Test
    fun matchValidRoomAliasWithMatrixULinkAndActionAttribute() {
        positiveRoomAliasTest("matrix:r/room:example.com?action=join", "room", "example.com")
    }

    @Test
    fun matchValidRoomAliasWithMatrixULinkAndViaAttribute() {
        positiveRoomAliasTest("matrix:r/room:example.com?via=example.com", "room", "example.com")
    }

    @Test
    fun matchValidRoomAliasWithMatrixULinkViaAndActionAttribute() {
        positiveRoomAliasTest("matrix:r/room:example.com?via=example.com&action=join", "room", "example.com")
    }

    fun matchValidRoomAliasWithMatrixULinkActionAndViaAttribute() {
        positiveRoomAliasTest("matrix:r/room:example.com?action=chat&via=example.com", "room", "example.com")
    }

    @Test
    fun matchValidRoomAliasWithMatrixULink() {
        positiveRoomAliasTest("matrix:r/room:example.com", "room", "example.com")
    }

    @Test
    fun matchValidRoomAliasWithSpecialCharacters() {
        positiveRoomAliasTest("#room:sub.example.com:8000", "room", "sub.example.com:8000")
    }

    @Test
    fun matchValidRoomAliasWithIPV4() {
        positiveRoomAliasTest("#room:1.1.1.1", "room", "1.1.1.1")
    }

    @Test
    fun matchValidRoomAliasWithIPV6() {
        positiveRoomAliasTest(
            "#room:[2001:0db8:85a3:0000:0000:8a2e:0370:7334]",
            "room",
            "[2001:0db8:85a3:0000:0000:8a2e:0370:7334]"
        )
    }

    @Test
    fun notMatchInvalidRoomAliasLocalpart() {
        negativeTest("#room&:example.com")
    }

    @Test
    fun notMatchInvalidRoomAliasDomain() {
        negativeTest("#room:ex&mple.com")
    }

    @Test
    fun notMatchInvalidRoomAliasIPV6WithIllegalCharacters() {
        negativeTest("#room:[2001:8a2e:0370:733G]")
    }

    @Test
    fun notMatchIncompleteRoomAliasHtmlTag() {
        negativeTest("""<a href="https://matrix.to/#/#room:example.com"""", MatrixRegex.roomHtmlAnchor)
    }

    @Test
    fun notMatchInvalidRoomAliasHtmlLinkTag() {
        negativeTest("<b href=\"https://matrix.to/#/#room:example.com>Room</b>", MatrixRegex.roomHtmlAnchor)
    }

    // Room IDs
    @Test
    fun matchValidRoomIdentifier() {
        positiveRoomIdTest("!a9._=-/+:example.com", "a9._=-/+", "example.com")
    }

    @Test
    fun matchValidRoomIdentifierWithMatrixToLink() {
        positiveRoomIdTest("<a href=\"https://matrix.to/#/!room:example.com\">Hallo</a>", "room", "example.com")
    }

    @Test
    fun matchValidRoomIdentifierWithMatrixToLinkWithoutHref() {
        positiveRoomIdTest("https://matrix.to/#/!room:example.com", "room", "example.com")
    }

    @Test
    fun matchValidRoomIdentifierWithMatrixULinkAndActionAttribute() {
        positiveRoomIdTest("matrix:roomid/room:example.com?action=join", "room", "example.com")
    }

    @Test
    fun matchValidRoomIdentifierWithMatrixULinkAndViaAttribute() {
        positiveRoomIdTest("matrix:roomid/room:example.com?via=example.com", "room", "example.com")
    }

    @Test
    fun matchValidRoomIdentifierWithMatrixULinkViaAndActionAttribute() {
        positiveRoomIdTest("matrix:roomid/room:example.com?via=example.com&action=join", "room", "example.com")
    }

    fun matchValidRoomIdentifierWithMatrixULinkActionAndViaAttribute() {
        positiveRoomIdTest("matrix:roomid/room:example.com?action=chat&via=example.com", "room", "example.com")
    }

    @Test
    fun matchValidRoomIdentifierWithMatrixULink() {
        positiveRoomIdTest("matrix:roomid/room:example.com", "room", "example.com")
    }

    @Test
    fun matchValidRoomIdentifierWithSpecialCharacters() {
        positiveRoomIdTest("!room:sub.example.com:8000", "room", "sub.example.com:8000")
    }

    @Test
    fun matchValidRoomIdentifierWithIPV4() {
        positiveRoomIdTest("!room:1.1.1.1", "room", "1.1.1.1")
    }

    @Test
    fun matchValidRoomIdentifierWithIPV6() {
        positiveRoomIdTest(
            "!room:[2001:0db8:85a3:0000:0000:8a2e:0370:7334]",
            "room",
            "[2001:0db8:85a3:0000:0000:8a2e:0370:7334]"
        )
    }

    @Test
    fun notMatchInvalidRoomIdLocalpart() {
        negativeTest("!ro om&:example.com")
    }

    @Test
    fun notMatchInvalidRoomIdDomain() {
        negativeTest("!room:ex&mple.com")
    }

    @Test
    fun notMatchInvalidRoomIdIPV6WithIllegalCharacters() {
        negativeTest("!room:[2001:8a2e:0370:733G]")
    }

    @Test
    fun notMatchIncompleteRoomIdHtmlTag() {
        negativeTest("""<a href="https://matrix.to/#/!room:example.com"""", MatrixRegex.roomHtmlAnchor)
    }

    @Test
    fun notMatchInvalidRoomIdHtmlLinkTag() {
        negativeTest("<b href=\"https://matrix.to/#/!room:example.com>User</b>", MatrixRegex.roomHtmlAnchor)
    }

    // Event IDs
    @Test
    fun matchValidEventIdentifier() {
        positiveEventIdTest("\$event", "\$event")
    }

    @Test
    fun matchValidEventIdentifierWithMatrixToLink() {
        positiveEventIdTest("<a href=\"https://matrix.to/#/!room:example.com/\$event\">Hallo</a>", "\$event")
    }

    @Test
    fun matchValidEventIdentifierWithMatrixToLinkWithoutHref() {
        positiveEventIdTest("https://matrix.to/#/!room:example.com/\$event", "\$event")
    }

    @Test
    fun matchValidEventIdentifierWithMatrixULinkAndActionAttribute() {
        positiveEventIdTest("matrix:roomid/room:example.com/e/event?action=join", "\$event")
    }

    @Test
    fun matchValidEventIdentifierWithMatrixULinkAndViaAttribute() {
        positiveEventIdTest("matrix:roomid/room:example.com/e/event?via=example.com", "\$event")
    }

    @Test
    fun matchValidEventIdentifierWithMatrixULinkViaAndActionAttribute() {
        positiveEventIdTest("matrix:roomid/room:example.com/e/event?via=example.com&action=join", "\$event")
    }

    fun matchValidEventIdentifierWithMatrixULinkActionAndViaAttribute() {
        positiveEventIdTest("matrix:roomid/room:example.com/e/event?action=chat&via=example.com", "\$event")
    }

    @Test
    fun matchValidEventIdentifierWithMatrixULink() {
        positiveEventIdTest("matrix:roomid/room:example.com/e/event", "\$event")
    }

    @Test
    fun notMatchInvalidEventIdLocalpart() {
        negativeTest("!eve t:example.com")
    }

    @Test
    fun notMatchIncompleteEventIdHtmlTag() {
        negativeTest("<a href=\"https://matrix.to/#/!room:example.com/\$event", MatrixRegex.roomHtmlAnchor)
    }

    @Test
    fun notMatchInvalidEventIdHtmlLinkTag() {
        negativeTest("<b href=\"https://matrix.to/#/!room:example.com/\$event>Event</b>", MatrixRegex.roomHtmlAnchor)
    }
}
