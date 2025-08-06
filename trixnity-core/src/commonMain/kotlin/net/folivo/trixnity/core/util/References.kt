package net.folivo.trixnity.core.util

import io.github.oshai.kotlinlogging.KotlinLogging
import net.folivo.trixnity.core.model.RoomAliasId
import net.folivo.trixnity.core.model.UserId

private val log = KotlinLogging.logger {}

object References {
    fun findMentions(message: String): Map<IntRange, Reference> {
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

    fun findIdMentions(content: String, from: Int = 0, to: Int = content.length): Map<IntRange, Reference> {
        return MatrixIdRegex.autolinkId
            .findAll(content, startIndex = from)
            .filter { it.range.last < to }
            .filter { it.range.last - it.range.first <= 255 }
            .mapNotNull { Pair(it.range, parseMatrixId(it.value) ?: return@mapNotNull null) }
            .toMap()
    }

    fun findLinkMentions(content: String, from: Int = 0, to: Int = content.length): Map<IntRange, Reference> {
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

    private fun parseMatrixId(id: String): Reference? {
        return when {
            id.length > 255 -> {
                log.trace { "malformed matrix id: id too long: ${id.length} (max length: 255)" }
                null
            }
            id.startsWith(UserId.sigilCharacter) -> Reference.User(UserId(id))
            id.startsWith(RoomAliasId.sigilCharacter) -> Reference.RoomAlias(RoomAliasId(id))
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