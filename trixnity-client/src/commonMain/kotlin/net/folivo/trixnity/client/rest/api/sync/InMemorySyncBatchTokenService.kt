package net.folivo.trixnity.client.rest.api.sync

import net.folivo.trixnity.core.model.MatrixId.UserId
import org.kodein.log.LoggerFactory
import org.kodein.log.newLogger
import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
object InMemorySyncBatchTokenService : SyncBatchTokenService {

    private val syncBatchTokenMap: MutableMap<UserId, String?> = mutableMapOf()

    private val defaultUserId = UserId("@default:trixnity")
    private val logger = newLogger(LoggerFactory.default)

    override suspend fun getBatchToken(userId: UserId?): String? {
        logger.warning { "Using InMemorySyncBatchTokenService. You should configure a persistence ${SyncBatchTokenService::class.simpleName}!" }
        return syncBatchTokenMap[userId ?: defaultUserId]
    }

    override suspend fun setBatchToken(value: String?, userId: UserId?) {
        syncBatchTokenMap[userId ?: defaultUserId] = value
    }

    fun reset() {
        syncBatchTokenMap.clear()
    }
}