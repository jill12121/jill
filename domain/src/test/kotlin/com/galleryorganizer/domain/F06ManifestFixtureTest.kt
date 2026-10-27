package com.galleryorganizer.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Drives the golden album manifest corpus in fixtures/f06/manifests through
 * the pure codec. Every fixture must match its declared expectation so that a
 * regression in either samples or validation is visible.
 */
class F06ManifestFixtureTest {
    @Test
    fun `golden manifest fixtures decode to expected outcomes`() {
        cases().forEach { case ->
            val result = AlbumManifestCodec.decode(readResource(case.resource))
            if (case.expectValid == "VALID") {
                val valid = assertIs<ManifestDecodeResult.Valid>(result, case.caseName)
                assertEquals(case.expectedState, valid.manifest.state.name, case.caseName)
                assertEquals(case.imageCount, valid.manifest.images.size, case.caseName)
                assertEquals(
                    AlbumManifestCodec.SUPPORTED_SCHEMA_VERSION,
                    valid.manifest.schemaVersion,
                    case.caseName,
                )
            } else {
                val invalid = assertIs<ManifestDecodeResult.Invalid>(result, case.caseName)
                val codes = invalid.issues.map { it.code.name }.distinct().sorted()
                assertEquals(case.issueCodes, codes, case.caseName)
                invalid.issues.forEach { assertTrue(it.field.isNotBlank(), case.caseName) }
            }
        }
    }

    private fun cases(): List<ManifestCase> {
        val stream = requireNotNull(
            javaClass.getResourceAsStream("/f06/manifest-cases.tsv"),
        )
        return stream.bufferedReader().useLines { lines ->
            lines.drop(1).filter(String::isNotBlank).map { line ->
                val columns = line.split('\t')
                require(columns.size == 6) { "Invalid manifest case row: $line" }
                ManifestCase(
                    columns[0], columns[1], columns[2],
                    if (columns[3] == "-") emptyList()
                    else columns[3].split(',').sorted(),
                    columns[4], columns[5].toInt(),
                )
            }.toList()
        }
    }

    private fun readResource(resource: String): String {
        val stream = requireNotNull(
            javaClass.getResourceAsStream("/$resource"),
        ) { "Missing fixture resource: $resource" }
        return stream.bufferedReader().use { it.readText() }
    }

    private data class ManifestCase(
        val caseName: String,
        val resource: String,
        val expectValid: String,
        val issueCodes: List<String>,
        val expectedState: String,
        val imageCount: Int,
    )
}
