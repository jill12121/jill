package com.galleryorganizer.spikes.s02.fixturehelper

enum class FixtureTokenAction { REUSE, CREATE }

object FixtureLaunchPolicy {
    fun shouldPrepareFixture(hasSavedInstanceState: Boolean): Boolean = !hasSavedInstanceState

    fun tokenAction(
        hasValidSavedToken: Boolean,
        savedFixtureExists: Boolean,
    ): FixtureTokenAction =
        if (hasValidSavedToken && savedFixtureExists) {
            FixtureTokenAction.REUSE
        } else {
            FixtureTokenAction.CREATE
        }
}
