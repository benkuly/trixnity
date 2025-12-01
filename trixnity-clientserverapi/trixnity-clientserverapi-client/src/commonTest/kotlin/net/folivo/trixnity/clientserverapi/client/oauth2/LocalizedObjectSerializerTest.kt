package net.folivo.trixnity.clientserverapi.client.oauth2

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
        val primitive: String = "primitive",
        val objekt: Map<String, String> = mapOf(
            "key" to "primitive"
        ),
        val localizedPrimitive: LocalizedField<String> = LocalizedField(
            default = "primitive",
            translations = mapOf(
                "de" to "Primitiv",
                "en" to "primitive"
            )
        ),
        val localizedObjekt: LocalizedField<Map<String, String>> = LocalizedField(
            default = mapOf(
                "key" to "primitive"
            ),
            translations = mapOf(
                "de" to mapOf("key" to "Primitiv"),
                "en" to mapOf("key" to "primitive")
            )
        ),
        val localizedPrimitiveNullDefault: LocalizedField<String?> = LocalizedField(
            default = null,
            translations = mapOf(
                "de" to "Primitiv",
                "en" to "primitive"
            )
        ),
        val localizedObjektNullDefault: LocalizedField<Map<String, String>> = LocalizedField(
            default = null,
            translations = mapOf(
                "de" to mapOf("key" to "Primitiv"),
                "en" to mapOf("key" to "primitive")
            )
        ),
        val localizedPrimitiveNullTranslations: LocalizedField<String?> = LocalizedField(
            default = "primitive",
            translations = null
        ),
        val localizedObjektNullTranslations: LocalizedField<Map<String, String>> = LocalizedField(
            default = mapOf(
                "key" to "primitive"
            ),
            translations = null
        ),
    )

    @OptIn(InternalSerializationApi::class)
    object TestClassSerializer : LocalizedObjectSerializer<TestClass>(TestClass.generatedSerializer())

    val json = Json {
        encodeDefaults = true
        prettyPrint = true
    }

    @Test
    fun serialize() {
        json.encodeToString(TestClass()) shouldBe """
            {
                "primitive": "primitive",
                "objekt": {
                    "key": "primitive"
                },
                "localizedPrimitive": "primitive",
                "localizedPrimitive#de": "Primitiv",
                "localizedPrimitive#en": "primitive",
                "localizedObjekt": {
                    "key": "primitive"
                },
                "localizedObjekt#de": {
                    "key": "Primitiv"
                },
                "localizedObjekt#en": {
                    "key": "primitive"
                },
                "localizedPrimitiveNullDefault": null,
                "localizedPrimitiveNullDefault#de": "Primitiv",
                "localizedPrimitiveNullDefault#en": "primitive",
                "localizedObjektNullDefault": null,
                "localizedObjektNullDefault#de": {
                    "key": "Primitiv"
                },
                "localizedObjektNullDefault#en": {
                    "key": "primitive"
                },
                "localizedPrimitiveNullTranslations": "primitive",
                "localizedObjektNullTranslations": {
                    "key": "primitive"
                }
            }
        """.trimIndent()
    }

    @Test
    fun deserialize() {
        json.decodeFromString<TestClass>(
            """
            {
                "primitive": "primitive",
                "objekt": {
                    "key": "primitive"
                },
                "localizedPrimitive": "primitive",
                "localizedPrimitive#de": "Primitiv",
                "localizedPrimitive#en": "primitive",
                "localizedObjekt": {
                    "key": "primitive"
                },
                "localizedObjekt#de": {
                    "key": "Primitiv"
                },
                "localizedObjekt#en": {
                    "key": "primitive"
                },
                "localizedPrimitiveNullDefault": null,
                "localizedPrimitiveNullDefault#de": "Primitiv",
                "localizedPrimitiveNullDefault#en": "primitive",
                "localizedObjektNullDefault": null,
                "localizedObjektNullDefault#de": {
                    "key": "Primitiv"
                },
                "localizedObjektNullDefault#en": {
                    "key": "primitive"
                },
                "localizedPrimitiveNullTranslations": "primitive",
                "localizedObjektNullTranslations": {
                    "key": "primitive"
                }
            }
        """.trimIndent()
        ) shouldBe TestClass()
    }
}