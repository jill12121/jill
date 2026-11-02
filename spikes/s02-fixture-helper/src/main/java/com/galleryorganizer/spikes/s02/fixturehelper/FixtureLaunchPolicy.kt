package com.galleryorganizer.spikes.s02.fixturehelper

object FixtureLaunchPolicy {
    fun shouldCreateFixture(hasSavedInstanceState: Boolean): Boolean = !hasSavedInstanceState
}
