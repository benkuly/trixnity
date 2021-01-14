package net.folivo.trixnity.client.rest.api.sync

import com.soywiz.klogger.Logger
import net.folivo.trixnity.core.model.MatrixId.UserId


class InMemorySyncBatchTokenService(
    private val syncBatchTokenMap: MutableMap<UserId, String?> = mutableMapOf()
) : SyncBatchTokenService {

    companion object {
        private val defaultUserId = UserId("@default:server")
        private val LOG = Logger("InMemorySyncBatchTokenService")
    }

    init {
        LOG.warn { "Using ${InMemorySyncBatchTokenService::class.simpleName}. You should configure a persistence ${SyncBatchTokenService::class.simpleName}!" }
    }

    override suspend fun getBatchToken(userId: UserId?): String? {
        return this.syncBatchTokenMap[userId ?: defaultUserId]
    }

    override suspend fun setBatchToken(value: String?, userId: UserId?) {
        syncBatchTokenMap[userId ?: defaultUserId] = value
    }

}