package com.galleryorganizer.domain

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AlbumManifestCodecTest {
    @Test
    fun `valid importing document decodes to the manifest model`() {
        val result = AlbumManifestCodec.decode(
            """
            {"schemaVersion":1,"albumId":"album-\u00e9","state":"IMPORTING",
            "createdAtEpochMillis":100,"committedAtEpochMillis":null,
            "images":[
            {"imageId":"ab12","fileName":"Day1/ab12.webp","order":0,"sizeBytes":10,
            "modifiedAtMillis":20,"fpMethod":"sha256","fpValue":"x"}]}
            """.trimIndent(),
        )
        val valid = assertIs<ManifestDecodeResult.Valid>(result)
        assertEquals(AlbumState.IMPORTING, valid.manifest.state)
        assertEquals("album-\u00e9", valid.manifest.albumId)
        assertEquals("Day1/ab12.webp", valid.manifest.images.single().fileName)
    }

    @Test
    fun `recovery-required is never accepted as a persisted state`() {
        val issues = decodeIssues(
            """{"schemaVersion":1,"albumId":"a","state":"RECOVERY_REQUIRED",
            "createdAtEpochMillis":1,"committedAtEpochMillis":null,"images":[]}""",
        )
        assertContains(codes(issues), ManifestIssueCode.INVALID_VALUE.name)
    }

    @Test
    fun `committed and importing commit-time rules are enforced`() {
        assertContains(
            codes(decodeIssues(
                """{"schemaVersion":1,"albumId":"a","state":"COMMITTED",
                "createdAtEpochMillis":1,"committedAtEpochMillis":null,"images":[]}""",
            )),
            ManifestIssueCode.INVALID_VALUE.name,
        )
        assertContains(
            codes(decodeIssues(
                """{"schemaVersion":1,"albumId":"a","state":"IMPORTING",
                "createdAtEpochMillis":1,"committedAtEpochMillis":2,"images":[]}""",
            )),
            ManifestIssueCode.INVALID_VALUE.name,
        )
    }

    @Test
    fun `duplicate ids are rejected case-insensitively and unknown states rejected`() {
        val json = """{"schemaVersion":1,"albumId":"a","state":"IMPORTING",
            "createdAtEpochMillis":1,"committedAtEpochMillis":null,
            "images":[
            {"imageId":"ab12","fileName":"ab12.webp","order":0,"sizeBytes":1,
            "modifiedAtMillis":2,"fpMethod":"sha256","fpValue":"x"},
            {"imageId":"AB12","fileName":"AB12.jpg","order":1,"sizeBytes":1,
            "modifiedAtMillis":2,"fpMethod":"sha256","fpValue":"y"}]}"""
        assertContains(codes(decodeIssues(json)), ManifestIssueCode.DUPLICATE_IMAGE_ID.name)
    }

    @Test
    fun `image file names stay relative and start with their short id`() {
        val json = """{"schemaVersion":1,"albumId":"a","state":"IMPORTING",
            "createdAtEpochMillis":1,"committedAtEpochMillis":null,
            "images":[
            {"imageId":"ab12","fileName":"../ab12.webp","order":0,"sizeBytes":1,
            "modifiedAtMillis":2,"fpMethod":"sha256","fpValue":"x"}]}"""
        val issues = decodeIssues(json)
        assertContains(codes(issues), ManifestIssueCode.INVALID_VALUE.name)
        assertTrue(issues.any { it.field == "images[0].fileName" })
    }

    @Test
    fun `unsupported missing and malformed documents report distinct codes`() {
        assertContains(
            codes(decodeIssues(
                """{"schemaVersion":2,"albumId":"a","state":"COMMITTED",
                "createdAtEpochMillis":1,"committedAtEpochMillis":2,"images":[]}""",
            )),
            ManifestIssueCode.UNSUPPORTED_SCHEMA.name,
        )
        assertEquals(
            listOf(ManifestIssueCode.MISSING_FIELD.name),
            codes(decodeIssues("{}")),
        )
        assertEquals(
            listOf(ManifestIssueCode.MALFORMED_JSON.name),
            codes(decodeIssues("{ broken")),
        )
        assertEquals(
            listOf(ManifestIssueCode.MALFORMED_JSON.name),
            codes(decodeIssues("""{"a":1} garbage""")),
        )
        assertEquals(
            listOf(ManifestIssueCode.MALFORMED_JSON.name),
            codes(decodeIssues("[1,2]")),
        )
    }

    @Test
    fun `blank album id and negative image size are invalid`() {
        val json = """{"schemaVersion":1,"albumId":"  ","state":"IMPORTING",
            "createdAtEpochMillis":1,"committedAtEpochMillis":null,
            "images":[
            {"imageId":"ab12","fileName":"ab12.webp","order":0,"sizeBytes":-1,
            "modifiedAtMillis":2,"fpMethod":"sha256","fpValue":"x"}]}"""
        val issues = decodeIssues(json)
        assertTrue(issues.any { it.field == "albumId" })
        assertTrue(issues.any { it.field == "images[0].sizeBytes" })
    }

    private fun codes(issues: List<ManifestIssue>): List<String> =
        issues.map { it.code.name }.distinct().sorted()

    private fun decodeIssues(json: String): List<ManifestIssue> {
        val result = AlbumManifestCodec.decode(json)
        return assertIs<ManifestDecodeResult.Invalid>(result).issues
    }
}
