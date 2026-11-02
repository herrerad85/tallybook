package com.oriondev.moneywallet.model

import android.os.Parcel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaceTest {

    @Test
    fun aParcelRoundTripKeepsEveryFieldOfAPlaceWithCoordinates() {
        val place = Place(7L, "Corner cafe", ColorIcon("#FF5722", "C"), "12 Main Street", 45.4642, -9.1900)
        val back = roundTrip(place)
        assertEquals(7L, back.getId())
        assertEquals("Corner cafe", back.getName())
        assertEquals("12 Main Street", back.getAddress())
        assertTrue(back.hasCoordinates())
        val coordinates = back.getCoordinates()
        assertNotNull(coordinates)
        assertEquals(45.4642, coordinates!!.getLatitude(), 0.0)
        assertEquals(-9.1900, coordinates.getLongitude(), 0.0)
    }

    @Test
    fun aParcelRoundTripKeepsAPlaceWithoutCoordinatesWithoutThem() {
        val place = Place(8L, "Market", ColorIcon("#3F51B5", "M"), "Old Square", null, null)
        val back = roundTrip(place)
        assertEquals(8L, back.getId())
        assertEquals("Market", back.getName())
        assertEquals("Old Square", back.getAddress())
        assertFalse(back.hasCoordinates())
        assertNull(back.getCoordinates())
    }

    @Test
    fun aParcelRoundTripReadsEachCoordinateFlagSoTheNextPlaceStillReads() {
        val parcel = Parcel.obtain()
        Place(1L, "Latitude only", null, "North", 10.5, null).writeToParcel(parcel, 0)
        Place(2L, "Longitude only", null, "East", null, 20.25).writeToParcel(parcel, 0)
        Place(3L, "Both", null, "Centre", -33.75, 151.5).writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val latitudeOnly = Place.CREATOR.createFromParcel(parcel)
        val longitudeOnly = Place.CREATOR.createFromParcel(parcel)
        val both = Place.CREATOR.createFromParcel(parcel)
        parcel.recycle()
        assertEquals(1L, latitudeOnly.getId())
        assertEquals("Latitude only", latitudeOnly.getName())
        assertEquals("North", latitudeOnly.getAddress())
        assertFalse(latitudeOnly.hasCoordinates())
        assertNull(latitudeOnly.getCoordinates())
        assertEquals(2L, longitudeOnly.getId())
        assertEquals("Longitude only", longitudeOnly.getName())
        assertEquals("East", longitudeOnly.getAddress())
        assertFalse(longitudeOnly.hasCoordinates())
        assertNull(longitudeOnly.getCoordinates())
        assertEquals(3L, both.getId())
        assertEquals("Both", both.getName())
        assertEquals("Centre", both.getAddress())
        assertTrue(both.hasCoordinates())
        assertEquals(-33.75, both.getCoordinates()!!.getLatitude(), 0.0)
        assertEquals(151.5, both.getCoordinates()!!.getLongitude(), 0.0)
    }

    private fun roundTrip(place: Place): Place {
        val parcel = Parcel.obtain()
        place.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val back = Place.CREATOR.createFromParcel(parcel)
        parcel.recycle()
        return back
    }
}
