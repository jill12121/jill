package com.galleryorganizer.domain

import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class F06AlgorithmsTest {
    @Test
    fun `batch grouping uses first directory and virtual loose batch`() {
        val grouped = BatchGrouper.group(
            listOf(
                SourceEntry("a", "Trip/Day1/a.jpg"),
                SourceEntry("b", "Trip/Day2/b.jpg"),
                SourceEntry("c", "loose.jpg"),
            ),
        )
        assertEquals(listOf("Trip", BatchGrouper.LOOSE_BATCH), grouped.map { it.batch })
        assertEquals(listOf("Day1", "Day2"), grouped.first().subgroups.map { it.name })
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
    fun `filename sanitizer applies NFC reserved names invalid chars and byte cap`() {
        assertEquals("é.txt", FileNameSanitizer.sanitize("e\u0301?.txt", 6))
        assertEquals("_CON", FileNameSanitizer.sanitize("CON"))
        val long = FileNameSanitizer.sanitize("图".repeat(100) + ".webp", 32)
        assertTrue(long.encodeToByteArray().size <= 32)
        assertTrue(long.endsWith(".webp"))
        assertTrue(Normalizer.isNormalized(long, Normalizer.Form.NFC))
    }

    @Test
    fun `short image id retries deterministically until unused`() {
        assertEquals(
            "abcdef12",
            ShortImageIdCollisionRetry.choose("abcdef123456", setOf("abcdef", "abcdef1")),
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
}
