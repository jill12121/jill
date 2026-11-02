package com.galleryorganizer.spikes.s01

import org.junit.Assert.assertEquals
import org.junit.Test

class S01RerunPolicyTest {
    @Test
    fun `same root retains previously passed manual retry evidence`() {
        val merged = S01RerunPolicy.merge(
            sameRoot = true,
            previous = S01ManualRetryEvidence(forceStop = true, reboot = true),
            current = S01ManualRetryEvidence(forceStop = false, reboot = false),
        )

        assertEquals(S01ManualRetryEvidence(forceStop = true, reboot = true), merged)
    }

    @Test
    fun `different root never inherits previous manual retry evidence`() {
        val merged = S01RerunPolicy.merge(
            sameRoot = false,
            previous = S01ManualRetryEvidence(forceStop = true, reboot = true),
            current = S01ManualRetryEvidence(forceStop = false, reboot = false),
        )

        assertEquals(S01ManualRetryEvidence(forceStop = false, reboot = false), merged)
    }

    @Test
    fun `same root never downgrades evidence from the current run`() {
        val merged = S01RerunPolicy.merge(
            sameRoot = true,
            previous = S01ManualRetryEvidence(forceStop = false, reboot = false),
            current = S01ManualRetryEvidence(forceStop = true, reboot = true),
        )

        assertEquals(S01ManualRetryEvidence(forceStop = true, reboot = true), merged)
    }
}
