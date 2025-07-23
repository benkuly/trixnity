package net.folivo.trixnity.core

import io.github.oshai.kotlinlogging.KotlinLogging
import net.folivo.trixnity.core.model.Mention
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.util.MatrixLinks
import net.folivo.trixnity.core.util.Patterns

private val log = KotlinLogging.logger {}

object MatrixRegex {
    // language=Regexp
    private const val ID_PATTERN = """[@#][0-9a-z\-.=_/+]+:(?:[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}|\[[0-9a-fA-F:.]{2,45}\]|[0-9a-zA-Z\-.]{1,255})(?::[0-9]{1,5})?"""
    private val idRegex = ID_PATTERN.toRegex()

    fun findMentions(message: String): Map<IntRange, Mention> {
        val links = findLinkMentions(message)
        val users = findIdMentions(message)
        val linksRange = links.keys.sortedBy { it.first }
        val uniqueUsers = users.filter { (user, _) ->
            // We don't want id matches that overlap with link matches,
            // as matrix.to urls will match both as link and as id
            !linksRange.overlaps(user)
        }
        return links.plus(uniqueUsers).toMap()
    }

    fun findIdMentions(content: String, from: Int = 0, to: Int = content.length): Map<IntRange, Mention> {
        return idRegex
            .findAll(content, startIndex = from)
            .filter { it.range.last < to }
            .filter { it.range.last - it.range.first <= 255 }
            .mapNotNull { Pair(it.range, parseMatrixId(it.value) ?: return@mapNotNull null) }
            .toMap()
    }

    fun findLinkMentions(content: String, from: Int = 0, to: Int = content.length): Map<IntRange, Mention> {
        return Patterns.AUTOLINK_MATRIX_URI
            .findAll(content, startIndex = from)
            .filter { it.range.last < to }
            .mapNotNull {
                val trimmedContent = it.value.trimLink()
                Pair(
                    it.range.first.until(it.range.first + trimmedContent.length),
                    MatrixLinks.parse(trimmedContent) ?: return@mapNotNull null
                )
            }.toMap()
    }

    fun findLinks(content: String, from: Int = 0, to: Int = content.length): Map<IntRange, String> {
        return Patterns.AUTOLINK_MATRIX_URI
            .findAll(content, startIndex = from)
            .filter { it.range.last < to }
            .map {
                val trimmedContent = it.value.trimLink()
                Pair(
                    it.range.first.until(it.range.first + trimmedContent.length),
                    trimmedContent,
                )
            }.toMap()
    }

    fun isValidUserId(id: String): Boolean =
        id.length <= 255
            && id.startsWith(UserId.sigilCharacter)
            && id.matches(idRegex)

    fun isValidRoomAliasId(id: String): Boolean =
        id.length <= 255
            && id.startsWith(RoomAliasId.sigilCharacter)
            && id.matches(idRegex)

    private fun parseMatrixId(id: String): Mention? {
        return when {
            id.length > 255 -> {
                log.trace { "malformed matrix id: id too long: ${id.length} (max length: 255)" }
                null
            }
            id.startsWith(UserId.sigilCharacter) -> Mention.User(UserId(id))
            id.startsWith(RoomAliasId.sigilCharacter) -> Mention.RoomAlias(RoomAliasId(id))
            else -> null
        }
    }

    private fun List<IntRange>.overlaps(user: IntRange): Boolean {
        val index = binarySearch { link ->
            when {
                user.last < link.first -> 1
                user.first > link.last -> -1
                user.first >= link.first && user.last <= link.last -> 0
                else -> -1
            }
        }
        return index >= 0
    }

    private fun String.trimParens(): String =
        if (endsWith(')')) {
            val trimmed = trimEnd(')')
            val openingParens = trimmed.count { it == '(' }
            val closingParens = trimmed.count { it == ')' }
            val endingParens = length - trimmed.length
            val openParens = openingParens - closingParens

            val desiredParens = minOf(endingParens, openParens)
            take(trimmed.length + desiredParens)
        } else this

    private fun String.trimLink(): String =
        trimEnd(',', '.', '!', '?', ':').trimParens()
}