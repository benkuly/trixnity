package net.folivo.trixnity.core

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.http.*
import net.folivo.trixnity.core.MatrixRegex.findMentions
import net.folivo.trixnity.core.model.*
import net.folivo.trixnity.test.utils.TrixnityBaseTest
import kotlin.test.Test
import kotlin.test.fail


class MatrixRegexTest : TrixnityBaseTest() {
    // Common Namespaced Identifier
    private fun commonNamespacedIdTest(id: String, expected: Boolean) {
        val message = "ID $id ist very common"
        val preamount = 3

        if (expected) {
            MatrixRegex.namespacedId.findAll(message).let {
                it.count() shouldBe 1 + preamount
                it.find { it.value == id }?.value shouldBe id
            }
        } else {
            MatrixRegex.namespacedId.findAll(message).find { it.value == id }?.value shouldNotBe id
        }
    }

    @Test
    fun shouldFailNamespacedWhenBiggerThan225Characters() {
        commonNamespacedIdTest("e".repeat(256), expected = false)
    }

    @Test
    fun shouldPassNamespacedWhenUnder255Characters() {
        commonNamespacedIdTest("e".repeat(255), expected = true)
    }

    @Test
    fun shouldFailNamespacedWhenStartingWithNumber() {
        commonNamespacedIdTest("7id", expected = false)
    }

    @Test
    fun shouldFailNamespacedWhenStartingWithMDot() {
        commonNamespacedIdTest("m.id", expected = false)
    }

    @Test
    fun shouldFailNamespacedWhenStartingWithDot() {
        commonNamespacedIdTest(".id", expected = false)
    }

    @Test
    fun shouldFailNamespacedWhenStartingWithUnderscore() {
        commonNamespacedIdTest("_id", expected = false)
    }

    @Test
    fun shouldFailNamespacedWhenStartingWithMinus() {
        commonNamespacedIdTest("-id", expected = false)
    }

    @Test
    fun shouldPassNamespacedWhenStartingWithLowercaseLetter() {
        commonNamespacedIdTest("super_id", expected = true)
    }

    @Test
    fun shouldFailNamespacedWhenContainingUppercaseLetter() {
        commonNamespacedIdTest("superID", expected = false)
    }

    @Test
    fun shouldFailNamespacedWhenContainingColon() {
        commonNamespacedIdTest("super:id", expected = false)
    }

    @Test
    fun shouldPassNamespacedWithoutInvalidCharacters() {
        commonNamespacedIdTest("xxx__6002-super.gamer-2006_xxx", expected = true)
    }

    // Server Name
    private fun serverNameTest(hostname: String, expected: Boolean) {
        val message = "Will host my server at $hostname wbu?"

        if (expected) {
            MatrixRegex.domain.findAll(message).let {
                it.count() shouldBe 1
                it.first().groupValues[0] shouldBe hostname
            }
        } else {
            MatrixRegex.domain.findAll(message).find { it.value == hostname }?.value shouldNotBe hostname
        }
    }

    @Test
    fun shouldPassDomainWithPort() {
        serverNameTest("matrix.org:8000", expected = true)
    }

    @Test
    fun shouldPassDomain() {
        serverNameTest("matrix.org", expected = true)
    }

    @Test
    fun shouldPassDomains() {
        serverNameTest("awesome.server.matrix.org", expected = true)
    }

    @Test
    fun shouldFailDomainWithIllegalSymboles() {
        serverNameTest("ex&mple.com", expected = false)
    }

    @Test
    fun shouldPassIPv4WithPort() {
        serverNameTest("1.2.3.4:1234", expected = true)
    }

    @Test
    fun shouldPassIPv4() {
        serverNameTest("1.2.3.4", expected = true)
    }

    @Test
    fun shouldPassIPV6WithPort() {
        serverNameTest("[1234:5678::abcd]:5678", expected = true)
    }

    @Test
    fun shouldPassIPV6() {
        serverNameTest("[1234:5678::abcd]", expected = true)
    }

    @Test
    fun shouldFailIPV6WithIllegalSymbols() {
        serverNameTest("[2001:8a2e:0370:733G]", expected = false)
    }

    // User Localpart
    private fun userLocalpartTest(localpart: String, expected: Boolean) {
        val message = "HALLO $localpart!"
        if (expected) {
            MatrixRegex.userIdLocalpart.findAll(message).let {
                it.count() shouldBe 1
                it.first().groupValues[0] shouldBe localpart
            }
        } else {
            MatrixRegex.userIdLocalpart.findAll(message).find { it.value == localpart }?.value shouldNotBe localpart
        }
    }

    @Test
    fun shouldFailEmptyUserLocalpart() {
        userLocalpartTest("", expected = false)
    }

    @Test
    fun shouldFailUserLocalpartWithUppercase() {
        userLocalpartTest("i-am_the.UNDEFINEDman/1+undefined=NaN", expected = false)
    }

    @Test
    fun shouldFailUserLocalpartWithIllegalSymboles() {
        userLocalpartTest("real&true", expected = false)
    }

    @Test
    fun shouldPassValidUserLocalpart() {
        userLocalpartTest("i-am_the.nullman/1+nullptr=nullptr", expected = true)
    }

    // Room Alias Localpart
    private fun roomAliasLocalpartTest(localpart: String, expected: Boolean) {
        val message = "HALLO $localpart !"
        val baseAmount = 2

        if (expected) {
            MatrixRegex.roomAliasLocalpart.findAll(message).let {
                it.count() shouldBe 1 + baseAmount
                it.iterator().next().next()?.value shouldBe localpart
            }
        } else {
            MatrixRegex.roomAliasLocalpart.findAll(message).find { it.value == localpart }?.value shouldNotBe localpart
        }
    }

    @Test
    fun shouldFailEmptyRoomAliasLocalpart() {
        roomAliasLocalpartTest("", expected = false)
    }

    @Test
    fun shouldPassRoomAliasLocalpartWithUppercase() {
        roomAliasLocalpartTest("i-am_the.UNDEFINEDman/1+undefined=NaN", expected = true)
    }

    @Test
    fun shouldFailRoomAliasLocalpartWithIllegalSymboles() {
        roomAliasLocalpartTest("real:true", expected = false)
    }

    @Test
    fun shouldPassValidRoomAliasLocalpart() {
        roomAliasLocalpartTest("i-am_the.nullman/1+nullptr=nullptr", expected = true)
    }

    // Opaque ID
    private fun opaqueIdTest(id: String, expected: Boolean) {
        val message = "HALLO $id!"
        val baseAmount = 1

        if (expected) {
            MatrixRegex.opaqueId.findAll(message).let {
                it.count() shouldBe 1 + baseAmount
                it.iterator().next().next()?.value shouldBe id
            }
        } else {
            MatrixRegex.opaqueId.findAll(message).find { it.value == id }?.value shouldNotBe id
        }
    }

    @Test
    fun shouldFailEmptyOpaqueId() {
        opaqueIdTest("", expected = false)
    }

    @Test
    fun shouldPassOpaqueIdWithUppercase() {
        opaqueIdTest("i-am_the.UNDEFINEDwoman1undefinedNaN", expected = true)
    }

    @Test
    fun shouldFailOpaqueIdWithIllegalSymboles() {
        opaqueIdTest("real&true", expected = false)
    }

    @Test
    fun shouldPassValidOpaqueId() {
        opaqueIdTest("i-am_the.invisible~woman", expected = true)
    }

    // User IDs
    private fun userIdTest(id: String, localpart: String, domain: String, expected: Boolean) {
        val result = findMentions("Hello $id :D")

        result.values.any {
            it.match == id
        } shouldBe expected

        if (expected) {
            result.size shouldBe 1
            (result.entries.first { it.value.match == id }.value as Mention.User).userId shouldBe UserId(
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
    fun shouldFailUserLocalpartContainingUppsercase() {
        userIdTest("@User:example.com", "User", "example.com", expected = false)
    }

    @Test
    fun shouldFailUserLocalpartContainingIllegalSymbole() {
        userIdTest("@user&:example.com", "user&", "example.com", expected = false)
    }

    @Test
    fun shouldFailInvalidUserDomain() {
        userIdTest("@user:ex&mple.com", "user", "ex&mple.com", expected = false)
    }

    @Test
    fun shouldFailInvalidUserIPV6WithIllegalCharacters() {
        userIdTest("@user:[2001:8a2e:0370:733G]", "user", "[2001:8a2e:0370:733G]", expected = false)
    }

    // Room IDs
    private fun roomIdTest(id: String, expected: Boolean) {
        val result = findMentions("omw to $id now")

        result.values.any {
            it.match == id
        } shouldBe expected

        if (expected) {
            result.size shouldBe 1
            (result.entries.first { it.value.match == id }.value as Mention.Room).roomId shouldBe RoomId(id)
        } else {
            result.size shouldBe 0
        }
    }

    @Test
    fun shouldPassRoomIdentifier() {
        roomIdTest("!a9._~B-:example.com", expected = true)
    }

    @Test
    fun shouldFailRoomIdOver255Bytes() {
        roomIdTest("!${"roomi".repeat(50)}:example.com", expected = false)
    }

    @Test
    fun shouldFailRoomIdWithIllegalSymboleInLocalpart() {
        roomIdTest("!room&:example.com", expected = false)
    }

    @Test
    fun shouldFailRoomIdWithNonOpaqueLocalpart() {
        roomIdTest("!ro+om:example.com", expected = false)
    }

    // Room Alias
    private fun roomAliasTest(id: String, localpart: String, domain: String, expected: Boolean) {
        val result = findMentions("omw to $id now")

        result.values.any {
            it.match == id
        } shouldBe expected

        if (expected) {
            result.size shouldBe 1
            (result.entries.first { it.value.match == id }.value as Mention.RoomAlias).roomAliasId shouldBe RoomAliasId(
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
        roomAliasTest("#roo:m:example.com", "room&", "example.com", expected = false)
    }

    @Test
    fun shouldFailInvalidRoomAliasDomain() {
        roomAliasTest("#room:ex&mple.com", "room", "ex&mple.com", expected = false)
    }

    @Test
    fun failFailRoomAliasIPV6WithIllegalCharacters() {
        roomAliasTest("#room:[2001:8a2e:0370:733G]", "room", "[2001:8a2e:0370:733G]", expected = false)
    }

    // Event IDs
    private fun eventIdTest(id: String, expected: Boolean) {
        val result = findMentions("You can find it at $id :)")

        result.values.any {
            it.match == id
        } shouldBe expected

        if (expected) {
            result.size shouldBe 1

            val mention = result.entries.first { it.value.match == id }.value
            if (mention !is Mention.Event) {
                fail("Wrong Mention type")
            } else {
                mention.eventId shouldBe EventId(id)
            }
        }
    }

    @Test
    fun matchValidEventIdentifier() {
        eventIdTest("\$event", expected = true)
    }

    @Test
    fun shouldFailEventIdWithSpace() {
        eventIdTest("\$eve t", expected = false)
    }

    @Test
    fun shouldFailNonOpaqueEventId() {
        eventIdTest("\$e+vent", expected = false)
    }

    // URIs
    private object UriTest {
        fun user(uri: String, localpart: String, domain: String, expected: Boolean) {
            val result = findMentions("Hello $uri :D")

            result.values.any {
                it.match == uri
            } shouldBe expected

            if (expected) {
                result.size shouldBe 1
                (result.entries.first { it.value.match == uri }.value as Mention.User).userId shouldBe UserId(
                    localpart,
                    domain
                )
            } else {
                result.size shouldBe 0
            }
        }

        fun roomId(uri: String, id: String, expected: Boolean) {
            val result = findMentions("omw to $uri now")

            result.values.any {
                it.match == uri
            } shouldBe expected

            if (expected) {
                result.size shouldBe 1
                (result.entries.first { it.value.match == uri }.value as Mention.Room).roomId shouldBe RoomId(id)
            }
        }

        fun roomAlias(uri: String, localpart: String, domain: String, expected: Boolean) {
            val result = findMentions("omw to $uri now")

            result.values.any {
                it.match == uri
            } shouldBe expected

            if (expected) {
                result.size shouldBe 1
                (result.entries.first { it.value.match == uri }.value as Mention.RoomAlias).roomAliasId shouldBe RoomAliasId(
                    localpart,
                    domain
                )
            } else {
                result.size shouldBe 0
            }
        }

        fun event(uri: String, roomId: String, eventId: String, expected: Boolean) {
            val result = findMentions("You can find it at $uri :)")

            result.values.any {
                it.match == uri
            } shouldBe expected

            if (expected) {
                result.size shouldBe 1

                val mention = result.entries.first { it.value.match == uri }.value
                if (mention !is Mention.Event) {
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

    // URIs: User ID
    @Test
    fun shouldPassUserURIWithActionQuery() {
        UriTest.user("matrix:u/user:example.com?action=chat", "user", "example.com", expected = true)
    }

    @Test
    fun shouldPassUserURIWithinAnchorTagWithActionQuery() {
        UriTest.user(
            "<a href=\"matrix:u/alice:example.org?action=chat\">Alice</a>",
            "alice",
            "example.org",
            expected = true
        )
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

    @Test
    fun shouldPassUriURIWithinAnchorTag() {
        UriTest.user(
            "<a href=\"matrix:u/demobot8:demo.example.de\">Dr. Karl Tanaka (Demo Bot)</a>",
            "demobot8",
            "demo.example.de",
            expected = true
        )
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
    fun shouldFailRoomIdURIWithIllegalQuery() {
        UriTest.roomId("matrix:roomid/room:example.com?actioné=messager", "!room:example.com", expected = false)
    }

    @Test
    fun shouldFailRoomIdURIWithReservedQuery() {
        UriTest.roomId("matrix:roomid/room:example.com?m.action=join", "!room:example.com", expected = false)
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
            val result = findMentions("Hello $permalink :D")

            result.values.any {
                it.match == permalink
            } shouldBe expected

            if (expected) {
                result.size shouldBe 1
                (result.entries.first { it.value.match == permalink }.value as Mention.User).userId shouldBe UserId(
                    localpart,
                    domain
                )
            } else {
                result.size shouldBe 0
            }
        }

        fun roomId(permalink: String, roomId: String, expected: Boolean) {
            val result = findMentions("omw to $permalink now")

            result.values.any {
                it.match == permalink
            } shouldBe expected

            if (expected) {
                result.size shouldBe 1
                (result.entries.first { it.value.match == permalink }.value as Mention.Room).roomId shouldBe
                        RoomId(roomId)
            } else {
                result.size shouldBe 0
            }
        }

        fun roomAlias(permalink: String, localpart: String, domain: String, expected: Boolean) {
            val result = findMentions("omw to $permalink now")

            result.values.any {
                it.match == permalink
            } shouldBe expected

            if (expected) {
                result.size shouldBe 1
                (result.entries.first { it.value.match == permalink }.value as Mention.RoomAlias).roomAliasId shouldBe RoomAliasId(
                    localpart,
                    domain
                )
            } else {
                result.size shouldBe 0
            }
        }

        fun event(permalink: String, roomId: String, eventId: String, expected: Boolean) {
            val result = findMentions("You can find it at $permalink :)")

            result.values.any {
                it.match == permalink
            } shouldBe expected

            if (expected) {
                result.size shouldBe 1

                val mention = result.entries.first { it.value.match == permalink }.value
                if (mention !is Mention.Event) {
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
    fun shouldPassUserPermalinkWithinAnchorTag() {
        PermalinkTest.user(
            "<a href=\"https://matrix.to/#/@user:example.com\">Hallo</a>",
            "user",
            "example.com",
            expected = true
        )
    }

    @Test
    fun shouldPassEncodedUserPermalinkWithinAnchorTag() {
        PermalinkTest.user(
            "<a href=\"https://matrix.to/#/%40user%3Aexample.com\">Hallo</a>",
            "user",
            "example.com",
            expected = true
        )
    }

    @Test
    fun shouldPassUserPermalink() {
        PermalinkTest.user("https://matrix.to/#/@user:example.com", "user", "example.com", expected = true)
    }

    @Test
    fun shouldPassEncodedUserPermalink() {
        PermalinkTest.user("https://matrix.to/#/%40alice%3Aexample.org", "alice", "example.org", expected = true)
    }

    @Test
    fun shouldPassUsersPermalinksWithinAnchorTag() {
        val karl = "<a href=\"https://matrix.to/#/@demobot8:demo.example.de\">Dr. Karl Tanaka (Demo Bot)</a>"
        val wolfgang =
            "<a href=\"https://matrix.to/#/@demobot2:demo.example.de\">Dr. Wolfgang Reidorf (Demo Bot)</a>"

        val message = "$karl und $wolfgang wie geht's euch?"

        val result = findMentions(message)
        result.size shouldBe 2

        result.values.any {
            it.match == karl
        } shouldBe true
        (result.entries.first { it.value.match == karl }.value as Mention.User).userId shouldBe UserId(
            "demobot8",
            "demo.example.de"
        )

        result.values.any {
            it.match == wolfgang
        } shouldBe true
        (result.entries.first { it.value.match == wolfgang }.value as Mention.User).userId shouldBe UserId(
            "demobot2",
            "demo.example.de"
        )
    }

    // Permalink: Room Alias
    @Test
    fun shouldPassRoomAliasPermalinkWithinAnchorTag() {
        PermalinkTest.roomAlias(
            "<a href=\"https://matrix.to/#/#room:example.com\">Hallo</a>",
            "room",
            "example.com",
            expected = true
        )
    }

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
    fun shouldPassRoomIdPermalinkWithinAnchorTag() {
        PermalinkTest.roomId(
            "<a href=\"https://matrix.to/#/!room:example.com\">Hallo</a>",
            "!room:example.com",
            expected = true
        )
    }

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
    fun shouldPassEventIDPermalinkWithinAnchorTag() {
        PermalinkTest.event(
            "<a href=\"https://matrix.to/#/!room:example.com/\$event\">Hallo</a>",
            "!room:example.com",
            "\$event",
            expected = true
        )
    }

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
            "\$event%3Aexample.org",
            expected = true
        )
    }

    // Parameters
    fun parameterTest(uri: String, params: Parameters, expected: Boolean) {
        val mentions = findMentions(uri)

        mentions.values.forEach {
            (it.parameters == params) shouldBe expected
        }
    }

    fun makeParameters(params: Map<String, String>): Parameters {
        return Parameters.build {
            params.forEach { (key, value) ->
                this.append(key, value)
            }
        }
    }

    @Test
    fun shouldPassValidViaParameter() {
        parameterTest(
            "matrix:roomid/somewhere%3Aexample.org/%24event%3Aexample.org?via=elsewhere.ca",
            makeParameters(mapOf("via" to "elsewhere.ca")),
            expected = true
        )
    }

    @Test
    fun shouldPassActionParameter() {
        parameterTest(
            "matrix:roomid/room:example.com/e/event?via=example.com&action=join",
            makeParameters(mapOf("action" to "join", "via" to "example.com")),
            expected = true
        )
    }

    @Test
    fun shouldPassActionAndViaParameter() {
        parameterTest(
            "matrix:roomid/somewhere%3Aexample.org?action=chat&via=example.com",
            makeParameters(mapOf("action" to "chat", "via" to "example.com")),
            expected = true
        )
    }

    @Test
    fun shouldPassCustomParameter() {
        parameterTest(
            "matrix:r/somewhere:example.org?foo=bar",
            makeParameters(mapOf("foo" to "bar")),
            expected = true
        )
    }

    @Test
    fun shouldFailCustomParameterWithIllegalCharacter() {
        parameterTest(
            "matrix:u/mario:esempio.it?actionaté=mammamia",
            makeParameters(mapOf("actionaté" to "mammamia")),
            expected = false
        )
    }

    @Test
    fun shouldFailCustomParameterWithIllegalStart() {
        parameterTest(
            "matrix:u/user:homeserver.рф?m.vector=matrix",
            makeParameters(mapOf("m.vector" to "matrix")),
            expected = false
        )
    }

    @Test
    fun shouldPassCustomParametersWithLastOneBeingIllegal() {
        parameterTest(
            "matrix:u/user:example.com?foo=bar&actionaté=mammamia",
            makeParameters(mapOf("foo" to "bar")),
            expected = true
        )
    }

    // Negative Edgecase
    private fun negativeTest(id: String, matcher: Regex? = null) {
        val message = "Hello $id :D"

        val result = matcher?.findAll(message)?.toList()?.size
            ?: findMentions(message).size

        result shouldBe 0
    }

    // Negative Edgecase: User ID
    @Test
    fun notMatchIncompleteUserHtmlTag() {
        negativeTest("""<a href="https://matrix.to/#/@user:example.com"""", MatrixRegex.userIdPermalinkAnchor)
    }

    @Test
    fun notMatchInvalidUserHtmlLinkTag() {
        negativeTest("<b href=\"https://matrix.to/#/@user:example.com>User</b>", MatrixRegex.userIdPermalinkAnchor)
    }

    // Negative Edgecase: Anchors
    @Test
    fun notMatchIncompleteRoomAliasHtmlTag() {
        negativeTest("""<a href="https://matrix.to/#/#room:example.com"""", MatrixRegex.roomAliasPermalinkAnchor)
    }

    @Test
    fun notMatchInvalidRoomAliasHtmlLinkTag() {
        negativeTest("<b href=\"https://matrix.to/#/#room:example.com>Room</b>", MatrixRegex.roomAliasPermalinkAnchor)
    }

    @Test
    fun notMatchIncompleteRoomIdHtmlTag() {
        negativeTest("""<a href="https://matrix.to/#/!room:example.com"""", MatrixRegex.roomIdPermalinkAnchor)
    }

    @Test
    fun notMatchInvalidRoomIdHtmlLinkTag() {
        negativeTest("<b href=\"https://matrix.to/#/!room:example.com>User</b>", MatrixRegex.roomIdPermalinkAnchor)
    }

    @Test
    fun notMatchIncompleteEventIdHtmlTag() {
        negativeTest("<a href=\"https://matrix.to/#/!room:example.com/\$event", MatrixRegex.eventIdPermalinkAnchor)
    }

    @Test
    fun notMatchInvalidEventIdHtmlLinkTag() {
        negativeTest(
            "<b href=\"https://matrix.to/#/!room:example.com/\$event>Event</b>",
            MatrixRegex.eventIdPermalinkAnchor
        )
    }
}
