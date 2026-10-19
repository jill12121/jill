package com.galleryorganizer.domain

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import kotlin.test.Test
import kotlin.test.assertTrue

class DomainArchitectureTest {
    @Test
    fun `domain production code has no platform dependencies`() {
        val scope = Konsist.scopeFromDirectory("domain/src/main")
        assertTrue(
            actual = scope.files.isNotEmpty(),
            message = "Domain production scope must not be empty.",
        )

        scope
            .files
            .assertFalse(strict = true) { file ->
                file.imports.any { import ->
                    import.name.startsWith("android.") ||
                        import.name.startsWith("androidx.compose.") ||
                        import.name.startsWith("androidx.room.")
                }
            }
    }
}
