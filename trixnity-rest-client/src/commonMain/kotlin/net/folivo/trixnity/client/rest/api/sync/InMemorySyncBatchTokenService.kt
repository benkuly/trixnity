package net.folivo.trixnity.client.rest.api.sync

import com.soywiz.klogger.Logger
import net.folivo.trixnity.core.model.MatrixId.UserId
import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
object InMemorySyncBatchTokenService : SyncBatchTokenService {

    private val syncBatchTokenMap: MutableMap<UserId, String?> = mutableMapOf()

    private val defaultUserId = UserId("@default:trixnity")
    private val LOG = Logger("InMemorySyncBatchTokenService")

    override suspend fun getBatchToken(userId: UserId?): String? {
        LOG.warn { "Using InMemorySyncBatchTokenService. You should configure a persistence ${SyncBatchTokenService::class.simpleName}!" }
        return syncBatchTokenMap[userId ?: defaultUserId]
    }

    override suspend fun setBatchToken(value: String?, userId: UserId?) {
        syncBatchTokenMap[userId ?: defaultUserId] = value
    }

    fun reset() {
        syncBatchTokenMap.clear()
    }
}