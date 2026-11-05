package com.oriondev.moneywallet.model

import com.oriondev.moneywallet.storage.preference.PreferenceManager

import org.junit.Assert.assertEquals
import org.junit.Test

class LockModeTest {

    @Test
    fun theNoneValueMapsToNone() {
        assertEquals(LockMode.NONE, LockMode.get(PreferenceManager.LOCK_MODE_NONE))
    }

    @Test
    fun thePinValueMapsToPin() {
        assertEquals(LockMode.PIN, LockMode.get(PreferenceManager.LOCK_MODE_PIN))
    }

    @Test
    fun theSequenceValueMapsToSequence() {
        assertEquals(LockMode.SEQUENCE, LockMode.get(PreferenceManager.LOCK_MODE_SEQUENCE))
    }

    @Test
    fun theFingerprintValueMapsToFingerprint() {
        assertEquals(LockMode.FINGERPRINT, LockMode.get(PreferenceManager.LOCK_MODE_FINGERPRINT))
    }

    @Test
    fun anUnknownValueMapsToNone() {
        assertEquals(LockMode.NONE, LockMode.get(42))
        assertEquals(LockMode.NONE, LockMode.get(-1))
    }

    @Test
    fun everyModeComesBackFromItsOwnValue() {
        assertEquals(4, LockMode.values().size)
        for (mode in LockMode.values()) {
            assertEquals(mode, LockMode.get(mode.getValue()))
        }
    }
}
