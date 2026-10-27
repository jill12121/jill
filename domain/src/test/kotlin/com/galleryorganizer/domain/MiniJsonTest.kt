package com.galleryorganizer.domain

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MiniJsonTest {
    @Test
    fun `parses nested objects arrays and scalar types`() {
        val value = MiniJson.parse(
            """
            {"schemaVersion":1,"ratio":1.5,"ok":true,"missing":null,
            "images":[{"id":"a"},{"id":"b"}]}
            """.trimIndent(),
        )
        val map = value as Map<*, *>
        assertEquals(1L, map["schemaVersion"])
        assertEquals(1.5, map["ratio"])
        assertEquals(true, map["ok"])
        assertTrue(map.containsKey("missing") && map["missing"] == null)
        val images = map["images"] as List<*>
        assertContentEquals(listOf("a", "b"), images.map { (it as Map<*, *>)["id"] })
    }

    @Test
    fun `decodes standard and unicode escapes`() {
        val map = MiniJson.parse("""{"v":"a\nb\tc\\d\/e\u00e9"}""") as Map<*, *>
        assertEquals("a\nb\tc\\d/e\u00e9", map["v"])
    }

    @Test
    fun `rejects malformed documents`() {
        listOf(
            "", "   ", "{", "[] {}", """{"a":1}garbage""",
            """{"a":}""", """{"a" 1}""", """[1,2,]""", """{"a":"x""",
            """{"a":"x	"}""", """{"a":"\q"}""", "01", "1.", "[1e]",
        ).forEach { document ->
            assertFailsWith<IllegalArgumentException>("document: $document") {
                MiniJson.parse(document)
            }
        }
    }

    @Test
    fun `integral values decode as long and fractions as double`() {
        val map = MiniJson.parse("""{"whole":1772000000000,"part":0.25}""") as Map<*, *>
        assertEquals(1772000000000L, map["whole"])
        assertTrue(map["whole"] is Long)
        assertTrue(map["part"] is Double)
    }
}
