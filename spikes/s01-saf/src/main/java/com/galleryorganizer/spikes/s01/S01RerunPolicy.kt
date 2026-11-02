package com.galleryorganizer.spikes.s01

data class S01ManualRetryEvidence(
    val forceStop: Boolean,
    val reboot: Boolean,
)

object S01RerunPolicy {
    fun merge(
        sameRoot: Boolean,
        previous: S01ManualRetryEvidence?,
        current: S01ManualRetryEvidence,
    ): S01ManualRetryEvidence {
        if (!sameRoot || previous == null) return current
        return S01ManualRetryEvidence(
            forceStop = previous.forceStop || current.forceStop,
            reboot = previous.reboot || current.reboot,
        )
    }
}
