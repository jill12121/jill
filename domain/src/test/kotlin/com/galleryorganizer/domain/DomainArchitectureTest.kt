package com.galleryorganizer.domain

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import kotlin.test.Test

class DomainArchitectureTest {
    @Test
    fun `domain production code has no platform dependencies`() {
        Konsist.scopeFromDirectory("domain/src/main")
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
