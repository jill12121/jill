package com.galleryorganizer.spikes.s02.fixture

import java.util.Base64

object ExternalFixtureContract {
    const val HELPER_PACKAGE = "com.galleryorganizer.spikes.s02.fixturehelper"
    const val SUITE_PACKAGE = "com.galleryorganizer.spikes.suite"
    const val PROBE_ACTIVITY = "com.galleryorganizer.spikes.s02.S02ProbeActivity"
    const val RUN_PERMISSION = "$SUITE_PACKAGE.permission.RUN_S02_FIXTURE"
    const val OPEN_ACTION = "com.galleryorganizer.spikes.s02.OPEN_EXTERNAL_FIXTURE"
    const val TOKEN_EXTRA = "s02.externalFixtureToken"
    const val RELATIVE_PATH = "Pictures/GalleryOrganizerProbe/"
    const val MIME_TYPE = "image/png"

    private val tokenPattern = Regex(
        """[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}""",
    )
    private val png = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk" +
            "YAAAAAYAAjCB0C8AAAAASUVORK5CYII=",
    )

    fun isValidToken(token: String): Boolean = tokenPattern.matches(token)
    fun displayName(token: String): String = ".s02-external-$token.png"
    fun payload(token: String): ByteArray {
        require(isValidToken(token))
        return png + "\nS02_EXTERNAL_FIXTURE:$token".toByteArray(Charsets.UTF_8)
    }
}
