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
    private const val ID_PATTERN = """[@#][0-9a-z\-.=_/+]+:(?:[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}|\[[0-9a-fA-F:.]{2,45}]|[0-9a-zA-Z\-.]{1,255})(?::[0-9]{1,5})?"""
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

    fun findIdMentions(content: String): Map<IntRange, Mention> {
        return idRegex.findAll(content)
            .filter { it.range.last - it.range.first <= 255 }
            .mapNotNull { Pair(it.range, parseMatrixId(it.value) ?: return@mapNotNull null) }
            .toMap()
    }

    fun findLinkMentions(content: String): Map<IntRange, Mention> {
        return Patterns.AUTOLINK_MATRIX_URI.findAll(content).mapNotNull {
            Pair(it.range, MatrixLinks.parse(it.value) ?: return@mapNotNull null)
        }.toMap()
    }

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

    private fun List<IntRange>.overlaps(match: IntRange): Boolean {
        val index = binarySearch { other ->
            when {
                other.first > match.first -> -1
                other.last < match.last -> 1
                else -> 0
            }
        }
        return index >= 0
    }
}