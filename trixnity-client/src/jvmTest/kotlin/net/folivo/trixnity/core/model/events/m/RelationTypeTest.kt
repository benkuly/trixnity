package net.folivo.trixnity.core.model.events.m

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import kotlin.test.assertEquals
import kotlin.test.fail


val testData = table(
    headers("relationType", "value"),
    row(RelationType.Annotation, "m.annotation"),
    row(RelationType.Reference, "m.reference"),
    row(RelationType.Replace, "m.replace"),
    row(RelationType.Reply, "m.in_reply_to"),
    row(RelationType.Thread, "m.thread"),
    row(RelationType.Unknown("io.element.thread"), "io.element.thread"),
    row(RelationType.Unknown("org.example.relationship"), "org.example.relationship"),
)

class RelationTypeTest : ShouldSpec({
    context(RelationType::class.simpleName!!) {
        forAll(testData) { relationType, value ->
            should("serialize $value correctly") {
                assertEquals(value, relationType.name)
            }
            should("deserialize $value correctly") {
                assertEquals(relationType, RelationType.of(value))
            }
        }
        should("include all variants in test data") {
            val allVariants = RelationType::class.sealedSubclasses.toSet()
            val testedVariants = testData.rows.map { it.a::class }.toSet()
            val untestedVariants = allVariants - testedVariants
            if (untestedVariants.isNotEmpty()) {
                fail("Found untested RelationType variants: ${untestedVariants.map { it.simpleName }}")
            }
        }
    }
})
