package com.galleryorganizer.domain

import com.galleryorganizer.contract.FileFingerprint
import com.galleryorganizer.contract.RelativePath
import com.galleryorganizer.contract.SourceFileRef
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Progressive pure-domain projection of the archive sequence: source entries
 * -> batches/subgroups -> sanitized short-id targets -> ORG plan -> IMPORTING
 * manifest -> per-file completion -> COMMITTED manifest -> filesystem verify.
 * No executor, I/O, Android, or storage port is involved.
 */
class F06ProgressiveE2ETest {
    @Test
    fun `archive sequence progresses from sources to a committed verified manifest`() {
        val rows = sourceRows()
        val entries = rows.map { SourceEntry(it.sourceId, it.relativePath) }

        val batches = BatchGrouper.group(entries).flatMap { group ->
            group.subgroups.flatMap { subgroup -> subgroup.sourceIds.map { it to group.batch } }
        }.toMap()
        rows.forEach { row -> assertEquals(row.expectedBatch, batches[row.sourceId], row.sourceId) }

        val keys = rows.map {
            SourceKeyNormalizer.normalize(
                "media", "primary", it.relativePath, it.sizeBytes, it.modifiedAt,
            )
        }
        assertEquals(keys.size, keys.distinct().size)

        val occupiedIds = mutableSetOf<String>()
        val images = rows.mapIndexed { index, row ->
            val segments = row.relativePath.replace('\\', '/').split('/')
            val sanitized = FileNameSanitizer.sanitize(segments.last())
            val extension = sanitized.substringAfterLast('.', "")
            val shortId = ShortImageIdCollisionRetry.choose(row.fullImageId, occupiedIds)
            occupiedIds += shortId.lowercase()
            val prefix = if (batches.getValue(row.sourceId) == BatchGrouper.LOOSE_BATCH) {
                ""
            } else {
                segments.subList(1, segments.lastIndex).joinToString("/") + "/"
            }
            val target = "$prefix$shortId.$extension"
            assertEquals(row.expectedTarget, target, row.sourceId)
            ManifestImage(
                imageId = shortId,
                fileName = target,
                order = index,
                sizeBytes = row.sizeBytes,
                modifiedAtMillis = row.modifiedAt,
                fpMethod = "sha256",
                fpValue = sha256(row.sourceId),
            )
        }

        val plan = PlanBuilder.build(
            planId = "plan-e2e",
            type = PlanType.ORG,
            snapshot = PlanSnapshot(scanGeneration = 7, draftRevision = 3),
            createdAtEpochMillis = 100,
            payload = OrgPayload(
                albumId = ALBUM_ID,
                immutableDir = RelativePath("Albums/$ALBUM_ID"),
                imports = rows.zip(images) { row, image ->
                    PlannedImport(
                        source = SourceLocator(
                            ref = SourceFileRef(row.sourceId),
                            fingerprint = FileFingerprint(
                                row.sizeBytes, row.modifiedAt, "image/webp",
                            ),
                        ),
                        target = RelativePath(image.fileName),
                        strategy = ImportStrategy.COPY_KEEP_SOURCE,
                    )
                },
            ),
            steps = buildList {
                add(PlanStepType.CREATE_DIR to ALBUM_ID)
                add(PlanStepType.WRITE_NOMEDIA to ALBUM_ID)
                add(PlanStepType.WRITE_MANIFEST to ALBUM_ID)
                images.forEach { image ->
                    add(PlanStepType.COPY_PART to image.fileName)
                    add(PlanStepType.PROMOTE_PART to image.fileName)
                }
                add(PlanStepType.COMMIT_MANIFEST to ALBUM_ID)
            },
        )
        assertEquals(emptyList(), PlanValidator.validate(plan))
        assertTrue(
            OrganizerStateMachines.canTransition(PlanState.READY, PlanState.EXECUTING),
        )
        assertTrue(
            OrganizerStateMachines.canTransition(PlanState.EXECUTING, PlanState.COMPLETED),
        )

        val importing = AlbumManifestFactory.importing(
            ALBUM_ID, createdAtEpochMillis = 100, images,
        )
        assertEquals(AlbumState.IMPORTING, importing.state)
        rows.indices.forEach { completed ->
            val partial = images.take(completed).map(::observed)
            assertEquals(
                emptyList(),
                ManifestVerifier.verify(importing, partial),
                "watermark $completed",
            )
        }
        val stray = observed(images.first()).copy(fileName = "stray.tmp")
        assertTrue(ManifestVerifier.verify(importing, listOf(stray)).isNotEmpty())

        val committed = AlbumManifestFactory.commit(importing, committedAtEpochMillis = 200)
        assertEquals(AlbumState.COMMITTED, committed.state)
        assertEquals(200L, committed.committedAtEpochMillis)
        assertEquals(emptyList(), ManifestVerifier.verify(committed, images.map(::observed)))
        val missingOne = images.drop(1).map(::observed)
        val mismatch = ManifestVerifier.verify(committed, missingOne)
        assertEquals(1, mismatch.size)
        assertEquals(ManifestIssueCode.FILESYSTEM_MISMATCH, mismatch.single().code)

        assertFailsWith<IllegalArgumentException> {
            AlbumManifestFactory.commit(committed, committedAtEpochMillis = 300)
        }
        assertFailsWith<IllegalArgumentException> {
            AlbumManifestFactory.commit(importing, committedAtEpochMillis = 50)
        }

        assertContentEquals(images.map { it.fileName }, rows.map { it.expectedTarget })
    }

    private fun observed(image: ManifestImage) = ObservedImageFile(
        fileName = image.fileName,
        sizeBytes = image.sizeBytes,
        modifiedAtMillis = image.modifiedAtMillis,
    )

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun sourceRows(): List<SourceRow> {
        val stream = requireNotNull(
            javaClass.getResourceAsStream("/f06/progressive-sources.tsv"),
        )
        return stream.bufferedReader().useLines { lines ->
            lines.drop(1).filter(String::isNotBlank).map { line ->
                val columns = line.split('\t')
                require(columns.size == 7) { "Invalid progressive row: $line" }
                SourceRow(
                    columns[0], columns[1], columns[2].toLong(), columns[3].toLong(),
                    columns[4], columns[5], columns[6],
                )
            }.toList()
        }
    }

    private data class SourceRow(
        val sourceId: String,
        val relativePath: String,
        val sizeBytes: Long,
        val modifiedAt: Long,
        val fullImageId: String,
        val expectedBatch: String,
        val expectedTarget: String,
    )

    private companion object {
        const val ALBUM_ID = "album-e2e"
    }
}
