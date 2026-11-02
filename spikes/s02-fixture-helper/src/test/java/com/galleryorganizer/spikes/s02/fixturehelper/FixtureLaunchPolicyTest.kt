package com.galleryorganizer.spikes.s02.fixturehelper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixtureLaunchPolicyTest {
    @Test
    fun `first launch creates fixture automatically`() {
        assertTrue(FixtureLaunchPolicy.shouldCreateFixture(hasSavedInstanceState = false))
    }

    @Test
    fun `restored activity does not create duplicate fixture`() {
        assertFalse(FixtureLaunchPolicy.shouldCreateFixture(hasSavedInstanceState = true))
    }
}
