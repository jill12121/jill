package com.galleryorganizer.domain

import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class F06AlgorithmsTest {
    @Test
    fun `batch grouping uses first directory and virtual loose batch`() {
        val grouped = BatchGrouper.group(
            listOf(
                SourceEntry("a", "Trip/Day1/a.jpg"),
                SourceEntry("b", "Trip/Day2/b.jpg"),
                SourceEntry("d", "Trip/Day2/Evening/d.jpg"),
                SourceEntry("c", "loose.jpg"),
            ),
        )
        assertEquals(listOf("Trip", BatchGrouper.LOOSE_BATCH), grouped.map { it.batch })
        assertEquals(
            listOf("Day1", "Day2", "Day2/Evening"),
            grouped.first().subgroups.map { it.name },
        )
        assertEquals(listOf("c"), grouped.last().subgroups.single().sourceIds)
    }

    @Test
    fun `source keys normalize separators dot segments and mtime precision`() {
        assertEquals(
            "MEDIA:primary:DCIM/Camera/a.jpg:9223372036854775807:123000",
            SourceKeyNormalizer.normalize(
                "media", "primary", "DCIM\\Camera/./a.jpg", Long.MAX_VALUE, 123456,
            ),
        )
    }

    @Test
    fun `source key rejects a path that escapes its root`() {
        assertFailsWith<IllegalArgumentException> {
            SourceKeyNormalizer.normalize("media", "primary", "../a.jpg", 1, 1_000)
        }
    }

    @Test
    fun `fixture table drives source identity grouping and filename cases`() {
        val acceptedKeys = mutableMapOf<String, String>()
        fixtures().forEach { fixture ->
            if (fixture.outcome == "REJECT") {
                assertFailsWith<IllegalArgumentException>(fixture.caseName) {
                    fixture.sourceKey()
                }
            } else {
                val key = fixture.sourceKey()
                acceptedKeys[fixture.caseName] = key
                assertTrue(key.contains(":${fixture.normalizedPath}:"))
                assertEquals(
                    fixture.batch,
                    BatchGrouper.group(listOf(SourceEntry(fixture.caseName, fixture.path)))
                        .single().batch,
                )
                assertEquals(
                    fixture.sanitizedName,
                    FileNameSanitizer.sanitize(
                        fixture.path.replace('\\', '/').substringAfterLast('/'),
                    ),
                )
            }
        }
        assertEquals(acceptedKeys["duplicate-a"], acceptedKeys["duplicate-b"])
        assertTrue(acceptedKeys["nfc-composed"] != acceptedKeys["nfc-decomposed"])
    }

    @Test
    fun `filename sanitizer applies NFC reserved names invalid chars and byte cap`() {
        assertEquals("é.txt", FileNameSanitizer.sanitize("e\u0301?.txt", 6))
        assertEquals("_CON", FileNameSanitizer.sanitize("CON"))
        val long = FileNameSanitizer.sanitize("图".repeat(100) + ".webp", 32)
        assertTrue(long.encodeToByteArray().size <= 32)
        assertTrue(long.endsWith(".webp"))
        assertTrue(Normalizer.isNormalized(long, Normalizer.Form.NFC))
        assertEquals("_", FileNameSanitizer.sanitize("图", 1))
    }

    @Test
    fun `filename sanitizer does not create a reserved name after truncation`() {
        assertEquals("_CO", FileNameSanitizer.sanitize("CONX", 3))
    }

    @Test
    fun `short image id retries deterministically until unused`() {
        assertEquals(
            "abcdef12",
            ShortImageIdCollisionRetry.choose("abcdef123456", setOf("abcdef", "abcdef1")),
        )
    }

    @Test
    fun `short image id supports the smallest valid prefix`() {
        assertEquals("a", ShortImageIdCollisionRetry.choose("abcdef", emptySet(), 1))
    }

    @Test
    fun `short image id treats case-only names as occupied`() {
        assertEquals(
            "abcdef1",
            ShortImageIdCollisionRetry.choose("abcdef123456", setOf("ABCDEF")),
        )
    }

    @Test
    fun `fingerprints compare only within the same method`() {
        assertTrue(
            FingerprintComparator.same(
                MethodFingerprint("sha256", "abc"),
                MethodFingerprint("sha256", "abc"),
            ),
        )
        assertFalse(
            FingerprintComparator.same(
                MethodFingerprint("sha256", "abc"),
                MethodFingerprint("fast", "abc"),
            ),
        )
    }

    @Test
    fun `plan builder indexes steps and validator reuses frozen schema`() {
        val plan = PlanBuilder.build(
            "p1", PlanType.METADATA, PlanSnapshot(1, 2), 10,
            MetadataPayload("a1", "{}"),
            listOf(
                PlanStepType.APPLY_MANIFEST_PATCH to "a1",
                PlanStepType.COMMIT_MANIFEST to "a1",
            ),
        )
        assertEquals(listOf(0, 1), plan.steps.map { it.index })
        assertEquals(emptyList(), PlanValidator.validate(plan))
    }

    @Test
    fun `selection snapshot is sorted detached and generation bound`() {
        val mutable = mutableListOf(
            SelectionCandidate("b", "B"),
            SelectionCandidate("a", "A"),
        )
        val snapshot = SelectionSnapshotMaterializer.materialize(7, 3, mutable, setOf("b", "a"))
        mutable.clear()
        assertEquals(listOf("a", "b"), snapshot.items.map { it.sourceKey })
        assertEquals(7, snapshot.scanGeneration)
        assertEquals(2, snapshot.items.size)
    }

    @Test
    fun `selection snapshot rejects duplicate candidate source keys`() {
        assertFailsWith<IllegalArgumentException> {
            SelectionSnapshotMaterializer.materialize(
                7,
                3,
                listOf(SelectionCandidate("a", "first"), SelectionCandidate("a", "second")),
                setOf("a"),
            )
        }
    }

    private fun fixtures(): List<SourceFixture> {
        val stream = requireNotNull(javaClass.getResourceAsStream("/f06/source-cases.tsv"))
        return stream.bufferedReader().useLines { lines ->
            lines.drop(1).filter(String::isNotBlank).map { line ->
                val columns = line.split('\t')
                require(columns.size == 10) { "Invalid fixture row: $line" }
                SourceFixture(
                    columns[0], columns[1], columns[2], columns[3],
                    columns[4].toLong(), columns[5].toLong(), columns[6],
                    columns[7], columns[8], columns[9],
                )
            }.toList()
        }
    }

    private data class SourceFixture(
        val caseName: String,
        val kind: String,
        val root: String,
        val path: String,
        val size: Long,
        val modifiedAtMillis: Long,
        val outcome: String,
        val normalizedPath: String,
        val batch: String,
        val sanitizedName: String,
    ) {
        fun sourceKey(): String = SourceKeyNormalizer.normalize(
            kind, root, path, size, modifiedAtMillis,
        )
    }
}
