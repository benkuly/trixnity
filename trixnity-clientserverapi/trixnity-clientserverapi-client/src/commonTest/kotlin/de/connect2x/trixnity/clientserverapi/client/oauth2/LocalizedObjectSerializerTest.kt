package de.connect2x.trixnity.clientserverapi.client.oauth2

import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test

class LocalizedObjectSerializerTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Serializable(with = TestClassSerializer::class)
    @KeepGeneratedSerializer
    data class TestClass(
        val Trixnity: String = "Trixnity",
        val objekt: Map<String, String> = mapOf(
            "key" to "Trixnity"
        ),
        val localizedTrixinity: LocalizedField<String> = LocalizedField(
            default = "Trixnity",
            translations = mapOf(
                "de" to "Trixinity",
                "en" to "Trixnity"
            )
        ),
        val localizedObjekt: LocalizedField<Map<String, String>> = LocalizedField(
            default = mapOf(
                "key" to "Trixnity"
            ),
            translations = mapOf(
                "de" to mapOf("key" to "Trixinity"),
                "en" to mapOf("key" to "Trixnity")
            )
        ),
        val localizedTrixinityNull: LocalizedField<String>? = null,
        val localizedTrixinityNullDefault: LocalizedField<String?>? = LocalizedField(
            default = null,
            translations = mapOf(
                "de" to "Trixinity",
                "en" to "Trixnity"
            )
        ),
        val localizedObjektNull: LocalizedField<Map<String, String>>? = null,
        val localizedObjektNullDefault: LocalizedField<Map<String, String>>? = LocalizedField(
            default = null,
            translations = mapOf(
                "de" to mapOf("key" to "Trixinity"),
                "en" to mapOf("key" to "Trixnity")
            )
        ),
        val localizedTrixinityNullTranslations: LocalizedField<String?> = LocalizedField(
            default = "Trixnity",
            translations = null
        ),
        val localizedObjektNullTranslations: LocalizedField<Map<String, String>> = LocalizedField(
            default = mapOf(
                "key" to "Trixnity"
            ),
            translations = null
        ),
    )

    @OptIn(InternalSerializationApi::class)
    internal object TestClassSerializer : LocalizedObjectSerializer<TestClass>(TestClass.generatedSerializer())

    val json = Json {
        explicitNulls = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val testClassString = """
            {
                "Trixnity": "Trixnity",
                "objekt": {
                    "key": "Trixnity"
                },
                "localizedTrixinity": "Trixnity",
                "localizedTrixinity#de": "Trixinity",
                "localizedTrixinity#en": "Trixnity",
                "localizedObjekt": {
                    "key": "Trixnity"
                },
                "localizedObjekt#de": {
                    "key": "Trixinity"
                },
                "localizedObjekt#en": {
                    "key": "Trixnity"
                },
                "localizedTrixinityNull": null,
                "localizedTrixinityNullDefault": null,
                "localizedTrixinityNullDefault#de": "Trixinity",
                "localizedTrixinityNullDefault#en": "Trixnity",
                "localizedObjektNull": null,
                "localizedObjektNullDefault": null,
                "localizedObjektNullDefault#de": {
                    "key": "Trixinity"
                },
                "localizedObjektNullDefault#en": {
                    "key": "Trixnity"
                },
                "localizedTrixinityNullTranslations": "Trixnity",
                "localizedObjektNullTranslations": {
                    "key": "Trixnity"
                }
            }
        """.trimIndent()

    @Test
    fun serialize() {
        json.encodeToString(TestClass()) shouldBe testClassString
    }

    @Test
    fun deserialize() {
        json.decodeFromString<TestClass>(testClassString) shouldBe TestClass()
    }
}