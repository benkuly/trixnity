package de.connect2x.trixnity.client.media.indexeddb

import io.kotest.assertions.json.shouldEqualJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.idb.schemaexporter.exportSchema
import kotlin.test.Test
import kotlin.time.Clock

class IndexedDBMediaStoreSchemaTest {

    private val databaseName = "media"

    private val expectedSchema = """
        {
          "name": "$databaseName",
          "version": 2,
          "stores": {
            "media": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {}
            },
            "tmp": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {}
            }
          }
        }
    """.trimIndent()

    @Test
    fun `schema should match`() = runTest {
        prepare()

        exportSchema(databaseName) shouldEqualJson expectedSchema
    }

    private suspend fun prepare() = coroutineScope {
        val backgroundJob = SupervisorJob(coroutineContext.job)
        val backgroundScope = CoroutineScope(coroutineContext + backgroundJob)

        IndexedDBMediaStore(
            databaseName,
            backgroundScope,
            MatrixClientConfiguration(),
            Clock.System
        )
            .init(backgroundScope)

        backgroundJob.cancel()
    }

}
