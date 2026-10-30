package com.whitebearcode02.checkmate.vehicle.data

import com.whitebearcode02.checkmate.vehicle.data.provider.SdkRequestTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SdkRequestTrackerTest {
    @Test
    fun timedOutRequestCannotCompleteANewerReadWhenItsCallbackArrivesLate() {
        val requests = SdkRequestTracker()
        val timedOut = requests.begin()
        assertTrue(requests.complete(timedOut))
        val current = requests.begin()
        assertFalse(requests.complete(timedOut))
        assertTrue(requests.isPending)
        assertTrue(requests.complete(current))
        assertFalse(requests.isPending)
    }

    @Test
    fun duplicateCallbacksCannotCompleteTheSameRequestTwice() {
        val requests = SdkRequestTracker()
        val ticket = requests.begin()
        assertTrue(requests.complete(ticket))
        assertFalse(requests.complete(ticket))
    }

    @Test(expected = IllegalStateException::class)
    fun aPendingRequestPreventsASecondConcurrentRead() {
        val requests = SdkRequestTracker()
        requests.begin()
        requests.begin()
    }
}
