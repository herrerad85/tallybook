package com.oriondev.moneywallet.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupTest {

    @Test
    fun typeZeroIsDaily() {
        assertEquals(Group.DAILY, Group.fromType(0))
    }

    @Test
    fun typeOneIsWeekly() {
        assertEquals(Group.WEEKLY, Group.fromType(1))
    }

    @Test
    fun typeTwoIsMonthly() {
        assertEquals(Group.MONTHLY, Group.fromType(2))
    }

    @Test
    fun typeThreeIsYearly() {
        assertEquals(Group.YEARLY, Group.fromType(3))
    }

    @Test
    fun everyGroupComesBackFromItsOwnType() {
        assertEquals(4, Group.values().size)
        for (group in Group.values()) {
            assertEquals(group, Group.fromType(group.getType()))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun anUnknownTypeThrows() {
        Group.fromType(4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aNegativeTypeThrows() {
        Group.fromType(-1)
    }
}
