package net.folivo.trixnity.core

import io.kotest.matchers.shouldBe
import net.folivo.trixnity.core.MatrixRegex.findMentions
import net.folivo.trixnity.core.model.UserId
import kotlin.test.Test


class MatrixRegexTest {
    fun positiveTest(id: String, localpart: String, domain: String, matcher: Regex? = null) {
        val message = "Hello $id"

        val result =
            if (matcher != null) findMentions(message, matcher)
            else findMentions(message)
        result.size shouldBe 1
        result[id] shouldBe UserId(localpart, domain)
    }

    fun negativeTest(id: String, matcher: Regex? = null) {
        val message = "Hello $id"

        val result =
            if (matcher != null) findMentions(message, matcher)
            else findMentions(message)
        println(result)

        result.size shouldBe 0
    }

    @Test
    fun matchValidUserIdentifier() {
        positiveTest("@a9._=-/+:example.com", "a9._=-/+", "example.com")
    }

    @Test
    fun matchValidUserIdentifierWithMatrixToLink() {
        positiveTest("<a href=\"https://matrix.to/#/@user:example.com\">Hallo</a>", "user", "example.com")
    }

    @Test
    fun matchValidUserIdentifierWithMatrixToLinkWithoutHref() {
        positiveTest("https://matrix.to/#/@user:example.com", "user", "example.com")
    }

    @Test
    fun matchValidUserIdentifierWithMatrixULinkAndActionAttribute() {
        positiveTest("matrix:u/user:example.com?action=chat", "user", "example.com")
    }

    @Test
    fun matchValidUserIdentifierWithMatrixULinkAndViaAttribute() {
        positiveTest("matrix:u/user:example.com?via=example.com", "user", "example.com")
    }

    @Test
    fun matchValidUserIdentifierWithMatrixULinkViaAndActionAttribute() {
        positiveTest("matrix:u/user:example.com?via=example.com&action=chat", "user", "example.com")
    }

    fun matchValidUserIdentifierWithMatrixULinkActionAndViaAttribute() {
        positiveTest("matrix:u/user:example.com?action=chat&via=example.com", "user", "example.com")
    }

    @Test
    fun matchValidUserIdentifierWithMatrixULink() {
        positiveTest("matrix:u/user:example.com", "user", "example.com")
    }

    @Test
    fun matchValidUserIdentifierWithSpecialCharacters() {
        positiveTest("@user:sub.example.com:8000", "user", "sub.example.com:8000")
    }

    @Test
    fun matchValidUserIdentifierWithIPV4() {
        positiveTest("@user:1.1.1.1", "user", "1.1.1.1")
    }

    @Test
    fun matchValidUserIdentifierWithIPV6() {
        positiveTest(
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
}