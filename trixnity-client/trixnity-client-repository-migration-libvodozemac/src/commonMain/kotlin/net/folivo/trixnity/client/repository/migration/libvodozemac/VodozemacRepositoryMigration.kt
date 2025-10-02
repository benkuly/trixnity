@file:OptIn(ExperimentalTime::class)

package net.folivo.trixnity.client.repository.migration.libvodozemac

import io.github.oshai.kotlinlogging.KotlinLogging
import net.folivo.trixnity.client.RepositoryMigration
import net.folivo.trixnity.client.store.repository.*
import net.folivo.trixnity.vodozemac.PickleKey
import net.folivo.trixnity.vodozemac.megolm.GroupSession
import net.folivo.trixnity.vodozemac.megolm.InboundGroupSession
import net.folivo.trixnity.vodozemac.olm.Account as OlmAccount
import net.folivo.trixnity.vodozemac.olm.Session
import kotlin.time.ExperimentalTime

private val log = KotlinLogging.logger("net.folivo.trixnity.client.repository.migration.libvodozemac.VodozemacRepositoryMigration")

data class VodozemacRepositoryMigration(
    val transactionManager: RepositoryTransactionManager,
    val migrationRepository: MigrationRepository,
    val accountRepository: AccountRepository,
    val olmAccountRepository: OlmAccountRepository,
    val olmSessionRepository: OlmSessionRepository,
    val outboundMegolmSessionRepository: OutboundMegolmSessionRepository,
    val inboundMegolmSessionRepository: InboundMegolmSessionRepository,
) : RepositoryMigration {
    override suspend fun run() {
        applyVodozemacMigrations()
    }
}

private const val MIGRATION_NAME = "VodozemacRepositoryMigration"

private enum class MigrationVersion(val value: String) {
    V1("1")
}

private suspend fun VodozemacRepositoryMigration.applyVodozemacMigrations() {
    suspend fun applyInitialMigration() {
        suspend fun save() {
            migrationRepository.save(MIGRATION_NAME, MigrationVersion.V1.value)
        }

        val account = accountRepository.get(1) ?: run { save(); return }

        val oldPickleKey = account.olmPickleKey ?: ""
        val newPickleKey = PickleKey(oldPickleKey.takeIf(String::isNotEmpty))

        fun OlmAccount.Companion.migrate(pickle: String)
                = vodozemacMigration(oldPickleKey, newPickleKey)(pickle)
        fun Session.Companion.migrate(pickle: String)
                = vodozemacMigration(oldPickleKey, newPickleKey)(pickle)
        fun GroupSession.Companion.migrate(pickle: String)
                = vodozemacMigration(oldPickleKey, newPickleKey)(pickle)
        fun InboundGroupSession.Companion.migrate(pickle: String)
                = vodozemacMigration(oldPickleKey, newPickleKey)(pickle)

        olmAccountRepository.updatePickle(OlmAccount::migrate)
        olmSessionRepository.updatePickles(Session::migrate)
        outboundMegolmSessionRepository.updatePickles(GroupSession::migrate)
        inboundMegolmSessionRepository.updatePickles(InboundGroupSession::migrate)


        accountRepository.save(1, account.copy(olmPickleKey = newPickleKey?.base64))

        save()
    }

    transactionManager.writeTransaction {
        val version = migrationRepository.get(MIGRATION_NAME)

        when (version) {
            null -> applyInitialMigration()
        }
    }
}

suspend fun OlmAccountRepository.updatePickle(block: suspend (String) -> String) {
    get(1)?.also { save(1, block(it)) }
}

private suspend fun OlmSessionRepository.updatePickles(block: suspend (String) -> String) {
    getAll()
        .asSequence()
        .mapNotNull { sessions ->
            sessions.firstOrNull()?.let { it.senderKey to sessions }
        }
        .forEach { (senderKey, sessions) ->
            save(
                senderKey,
                sessions.mapTo(HashSet(sessions.size)) { session -> session.copy(pickled = block(session.pickled)) }
            )
        }
}

private suspend fun OutboundMegolmSessionRepository.updatePickles(block: suspend (String) -> String) {
    getAll()
        .asSequence()
        .forEach { session ->
            save(
                session.roomId,
                session.copy(pickled = block(session.pickled)),
            )
        }
}

private suspend fun InboundMegolmSessionRepository.updatePickles(block: suspend (String) -> String) {
    getAll()
        .asSequence()
        .forEach { session ->
            save(
                InboundMegolmSessionRepositoryKey(
                    sessionId = session.sessionId,
                    roomId = session.roomId,
                ),
                session.copy(pickled = block(session.pickled))
            )
        }
}

private typealias PickleMigration = (oldPickle: String) -> String

private inline fun <T> pickleMigration(
    name: String,
    oldPickleKey: String,
    newPickleKey: PickleKey? = null,
    crossinline fromLibolmPickle: (pickle: String, key: String) -> T,
    crossinline intoPickle: T.(PickleKey?) -> String,
): PickleMigration = { oldPickle ->
    log.trace { "Migrating legacy $name: $oldPickle"}
    val newPickle = fromLibolmPickle(oldPickle, oldPickleKey).intoPickle(newPickleKey)
    log.debug { "Migrated $name (old=$oldPickle, new=$newPickle"}
    newPickle
}

private fun OlmAccount.Companion.vodozemacMigration(
    oldPickleKey: String,
    newPickleKey: PickleKey? = null,
): PickleMigration = pickleMigration(
    name = "Account",
    oldPickleKey = oldPickleKey,
    newPickleKey = newPickleKey,
    fromLibolmPickle = ::fromLibolmPickle,
    intoPickle = OlmAccount::pickle
)

private fun Session.Companion.vodozemacMigration(
    oldPickleKey: String,
    newPickleKey: PickleKey? = null,
): PickleMigration = pickleMigration(
    name = "Session",
    oldPickleKey = oldPickleKey,
    newPickleKey = newPickleKey,
    fromLibolmPickle = ::fromLibolmPickle,
    intoPickle = Session::pickle
)

private fun GroupSession.Companion.vodozemacMigration(
    oldPickleKey: String,
    newPickleKey: PickleKey? = null,
): PickleMigration = pickleMigration(
    name = "GroupSession",
    oldPickleKey = oldPickleKey,
    newPickleKey = newPickleKey,
    fromLibolmPickle = ::fromLibolmPickle,
    intoPickle = GroupSession::pickle
)

private fun InboundGroupSession.Companion.vodozemacMigration(
    oldPickleKey: String,
    newPickleKey: PickleKey? = null,
): PickleMigration = pickleMigration(
    name = "InboundGroupSession",
    oldPickleKey = oldPickleKey,
    newPickleKey = newPickleKey,
    fromLibolmPickle = ::fromLibolmPickle,
    intoPickle = InboundGroupSession::pickle
)