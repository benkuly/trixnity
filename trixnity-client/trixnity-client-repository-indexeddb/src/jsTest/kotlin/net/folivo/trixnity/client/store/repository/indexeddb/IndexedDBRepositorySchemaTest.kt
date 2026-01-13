package net.folivo.trixnity.client.store.repository.indexeddb

import io.kotest.assertions.json.shouldEqualJson
import kotlinx.coroutines.test.runTest
import net.folivo.trixnity.idb.schemaexporter.exportSchema
import kotlin.test.Test

class IndexedDBRepositorySchemaTest {

    private val databaseName = "repository"

    private val schema = """
        {
          "name": "repository",
          "version": 9,
          "stores": {
            "account": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {}
            },
            "authentication": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {}
            },
            "cross_signing_keys": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {}
            },
            "device_keys": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {}
            },
            "global_account_data": {
              "keyPath": [
                "type",
                "key"
              ],
              "autoIncrement": false,
              "indexes": {
                "type": {
                  "keyPath": "type",
                  "unique": false,
                  "multiEntry": false
                }
              }
            },
            "inbound_megolm_message_index": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {}
            },
            "inbound_megolm_session": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {
                "hasBeenBackedUp": {
                  "keyPath": "hasBeenBackedUp",
                  "unique": false,
                  "multiEntry": false
                }
              }
            },
            "key_chain_link": {
              "keyPath": [
                "signingUserId",
                "signingKeyId",
                "signingKeyValue",
                "signedUserId",
                "signedKeyId",
                "signedKeyValue"
              ],
              "autoIncrement": false,
              "indexes": {
                "signed": {
                  "keyPath": [
                    "signedUserId",
                    "signedKeyId",
                    "signedKeyValue"
                  ],
                  "unique": false,
                  "multiEntry": false
                },
                "signing": {
                  "keyPath": [
                    "signingUserId",
                    "signingKeyId",
                    "signingKeyValue"
                  ],
                  "unique": false,
                  "multiEntry": false
                }
              }
            },
            "key_verification_state": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {}
            },
            "media_cache_mapping": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {}
            },
            "migration": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {}
            },
            "notification": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {
                "roomId": {
                  "keyPath": "roomId",
                  "unique": false,
                  "multiEntry": false
                }
              }
            },
            "notification_state": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {}
            },
            "notification_update": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {
                "roomId": {
                  "keyPath": "roomId",
                  "unique": false,
                  "multiEntry": false
                }
              }
            },
            "olm_account": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {}
            },
            "olm_forget_fallback_key_after": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {}
            },
            "olm_session": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {}
            },
            "outbound_megolm_session": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {}
            },
            "outdated_keys": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {}
            },
            "room": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {}
            },
            "room_account_data": {
              "keyPath": [
                "roomId",
                "type",
                "key"
              ],
              "autoIncrement": false,
              "indexes": {
                "roomId": {
                  "keyPath": "roomId",
                  "unique": false,
                  "multiEntry": false
                },
                "roomId|type": {
                  "keyPath": [
                    "roomId",
                    "type"
                  ],
                  "unique": false,
                  "multiEntry": false
                }
              }
            },
            "room_key_request": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {}
            },
            "room_outbox_message_2": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {
                "roomId": {
                  "keyPath": "roomId",
                  "unique": false,
                  "multiEntry": false
                }
              }
            },
            "room_state": {
              "keyPath": [
                "roomId",
                "type",
                "stateKey"
              ],
              "autoIncrement": false,
              "indexes": {
                "roomId": {
                  "keyPath": "roomId",
                  "unique": false,
                  "multiEntry": false
                },
                "roomId|type": {
                  "keyPath": [
                    "roomId",
                    "type"
                  ],
                  "unique": false,
                  "multiEntry": false
                },
                "type|stateKey": {
                  "keyPath": [
                    "type",
                    "stateKey"
                  ],
                  "unique": false,
                  "multiEntry": false
                }
              }
            },
            "room_user": {
              "keyPath": [
                "roomId",
                "userId"
              ],
              "autoIncrement": false,
              "indexes": {
                "roomId": {
                  "keyPath": "roomId",
                  "unique": false,
                  "multiEntry": false
                }
              }
            },
            "room_user_receipts": {
              "keyPath": [
                "roomId",
                "userId"
              ],
              "autoIncrement": false,
              "indexes": {
                "roomId": {
                  "keyPath": "roomId",
                  "unique": false,
                  "multiEntry": false
                }
              }
            },
            "secret": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {}
            },
            "secret_key_request": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {}
            },
            "server_data": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {}
            },
            "timeline_event": {
              "keyPath": null,
              "autoIncrement": false,
              "indexes": {
                "roomId": {
                  "keyPath": "event.room_id",
                  "unique": false,
                  "multiEntry": false
                }
              }
            },
            "timeline_event_relation": {
              "keyPath": [
                "roomId",
                "relatedEventId",
                "relationType",
                "eventId"
              ],
              "autoIncrement": false,
              "indexes": {
                "roomId": {
                  "keyPath": "roomId",
                  "unique": false,
                  "multiEntry": false
                },
                "roomId|relatedEventId|relationType": {
                  "keyPath": [
                    "roomId",
                    "relatedEventId",
                    "relationType"
                  ],
                  "unique": false,
                  "multiEntry": false
                }
              }
            },
            "user_presence": {
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

        exportSchema(databaseName) shouldEqualJson schema
    }

    private suspend fun prepare() { createDatabase(databaseName) }

}
