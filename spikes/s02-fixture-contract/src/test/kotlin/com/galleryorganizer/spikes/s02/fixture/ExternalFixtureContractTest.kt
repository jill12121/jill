package com.galleryorganizer.spikes.s02.fixture

import kotlin.test.Test
import kotlin.test.assertEquals

class ExternalFixtureContractTest {
    @Test
    fun `helper targets the protected S02 activity directly`() {
        assertEquals(
            "com.galleryorganizer.spikes.s02.S02ProbeActivity",
            ExternalFixtureContract.PROBE_ACTIVITY,
        )
        assertEquals(
            "com.galleryorganizer.spikes.suite.permission.RUN_S02_FIXTURE",
            ExternalFixtureContract.RUN_PERMISSION,
        )
    }
}
