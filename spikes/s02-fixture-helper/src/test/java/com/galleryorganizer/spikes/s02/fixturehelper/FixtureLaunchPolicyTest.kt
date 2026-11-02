package com.galleryorganizer.spikes.s02.fixturehelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixtureLaunchPolicyTest {
    @Test
    fun `first launch prepares a fixture automatically`() {
        assertTrue(FixtureLaunchPolicy.shouldPrepareFixture(hasSavedInstanceState = false))
    }

    @Test
    fun `restored activity does not create duplicate fixture`() {
        assertFalse(FixtureLaunchPolicy.shouldPrepareFixture(hasSavedInstanceState = true))
    }

    @Test
    fun `valid saved fixture is reused`() {
        assertEquals(
            FixtureTokenAction.REUSE,
            FixtureLaunchPolicy.tokenAction(
                hasValidSavedToken = true,
                savedFixtureExists = true,
            ),
        )
    }

    @Test
    fun `missing or invalid saved fixture creates a new token`() {
        listOf(
            false to false,
            false to true,
            true to false,
        ).forEach { (validToken, fixtureExists) ->
            assertEquals(
                FixtureTokenAction.CREATE,
                FixtureLaunchPolicy.tokenAction(validToken, fixtureExists),
            )
        }
    }
}
