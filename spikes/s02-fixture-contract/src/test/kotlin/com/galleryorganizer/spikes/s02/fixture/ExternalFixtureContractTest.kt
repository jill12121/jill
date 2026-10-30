package com.galleryorganizer.spikes.s02.fixture

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalFixtureContractTest {
    @Test
    fun `token identifies the helper-owned fixture without launch coupling`() {
        val token = "123e4567-e89b-12d3-a456-426614174000"

        assertTrue(ExternalFixtureContract.isValidToken(token))
        assertEquals(".s02-external-$token.png", ExternalFixtureContract.displayName(token))
        assertContains(
            ExternalFixtureContract.payload(token).decodeToString(),
            "S02_EXTERNAL_FIXTURE:$token",
        )
    }
}
