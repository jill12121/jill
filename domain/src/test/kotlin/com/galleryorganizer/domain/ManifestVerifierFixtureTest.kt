package com.galleryorganizer.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ManifestVerifierFixtureTest {
    @Test
    fun `filesystem observation fixtures match expected verification`() {
        cases().forEach { case ->
            val decoded = AlbumManifestCodec.decode(readResource(case.manifest))
            val manifest = assertIs<ManifestDecodeResult.Valid>(decoded, case.caseName).manifest
            val issues = ManifestVerifier.verify(manifest, readObserved(case.observed))
            if (case.expectOutcome == "CLEAN") {
                assertEquals(emptyList(), issues, case.caseName)
            } else {
                assertEquals(case.expectedFields, issues.map { it.field }.sorted(), case.caseName)
                assertTrue(
                    issues.all { it.code == ManifestIssueCode.FILESYSTEM_MISMATCH },
                    case.caseName,
                )
            }
        }
    }

    @Test
    fun `mtime is compared at the declared resolution`() {
        val manifest = manifest(
            state = AlbumState.COMMITTED,
            images = listOf(image("mm01.webp", modifiedAt = 1_000_500)),
        )
        val sameBucket = listOf(ObservedImageFile("mm01.webp", 100, 1_000_900))
        assertEquals(emptyList(), ManifestVerifier.verify(manifest, sameBucket))
        val otherBucket = listOf(ObservedImageFile("mm01.webp", 100, 1_001_500))
        assertEquals(1, ManifestVerifier.verify(manifest, otherBucket).size)
    }

    @Test
    fun `duplicate observed names are reported as mismatch`() {
        val manifest = manifest(
            state = AlbumState.COMMITTED,
            images = listOf(image("mm01.webp", modifiedAt = 1000)),
        )
        val issues = ManifestVerifier.verify(
            manifest,
            listOf(
                ObservedImageFile("mm01.webp", 100, 1000),
                ObservedImageFile("mm01.webp", 100, 1000),
            ),
        )
        assertEquals(1, issues.size)
        assertEquals(ManifestIssueCode.FILESYSTEM_MISMATCH, issues.single().code)
    }

    private fun manifest(
        state: AlbumState,
        images: List<ManifestImage>,
    ) = AlbumManifest(
        schemaVersion = AlbumManifestCodec.SUPPORTED_SCHEMA_VERSION,
        albumId = "album-test",
        state = state,
        createdAtEpochMillis = 100,
        committedAtEpochMillis = if (state == AlbumState.COMMITTED) 200 else null,
        images = images,
    )

    private fun image(fileName: String, modifiedAt: Long) = ManifestImage(
        imageId = fileName.substringBefore('.'),
        fileName = fileName,
        order = 0,
        sizeBytes = 100,
        modifiedAtMillis = modifiedAt,
        fpMethod = "sha256",
        fpValue = "x",
    )

    private fun cases(): List<FilesystemCase> {
        val stream = requireNotNull(
            javaClass.getResourceAsStream("/f06/filesystem-cases.tsv"),
        )
        return stream.bufferedReader().useLines { lines ->
            lines.drop(1).filter(String::isNotBlank).map { line ->
                val columns = line.split('\t')
                require(columns.size == 5) { "Invalid filesystem case row: $line" }
                FilesystemCase(
                    columns[0], columns[1], columns[2], columns[3],
                    if (columns[4] == "-") emptyList() else columns[4].split(',').sorted(),
                )
            }.toList()
        }
    }

    private fun readObserved(resource: String): List<ObservedImageFile> {
        val stream = requireNotNull(javaClass.getResourceAsStream("/$resource"))
        return stream.bufferedReader().useLines { lines ->
            lines.drop(1).filter(String::isNotBlank).map { line ->
                val columns = line.split('\t')
                require(columns.size == 3) { "Invalid observed row: $line" }
                ObservedImageFile(columns[0], columns[1].toLong(), columns[2].toLong())
            }.toList()
        }
    }

    private fun readResource(resource: String): String {
        val stream = requireNotNull(
            javaClass.getResourceAsStream("/$resource"),
        ) { "Missing fixture resource: $resource" }
        return stream.bufferedReader().use { it.readText() }
    }

    private data class FilesystemCase(
        val caseName: String,
        val manifest: String,
        val observed: String,
        val expectOutcome: String,
        val expectedFields: List<String>,
    )
}
