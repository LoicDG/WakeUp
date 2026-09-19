package com.loic.wakeup.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeoPointTest {

    @Test
    fun samePoint_isZeroMetersAway() {
        val p = GeoPoint(45.5017, -73.5673)
        assertEquals(0.0, p.distanceTo(p), 1e-6)
    }

    @Test
    fun oneThousandthOfADegreeOfLatitude_isAbout111Meters() {
        val a = GeoPoint(45.5000, -73.5673)
        val b = GeoPoint(45.5010, -73.5673)
        assertEquals(111.2, a.distanceTo(b), 0.5)
    }

    @Test
    fun montrealToQuebecCity_isAbout233Km() {
        val montreal = GeoPoint(45.5017, -73.5673)
        val quebec = GeoPoint(46.8139, -71.2080)
        assertEquals(233_000.0, montreal.distanceTo(quebec), 2_000.0)
    }

    @Test
    fun parse_acceptsMapAppFormat() {
        assertEquals(GeoPoint(45.5017, -73.5673), GeoPoint.parse("45.5017, -73.5673"))
        assertEquals(GeoPoint(45.5017, -73.5673), GeoPoint.parse("  45.5017 -73.5673 "))
        assertEquals(GeoPoint(45.0, -73.0), GeoPoint.parse("45;-73"))
    }

    @Test
    fun parse_rejectsMalformedOrOutOfRange() {
        assertNull(GeoPoint.parse(""))
        assertNull(GeoPoint.parse("45.5017"))
        assertNull(GeoPoint.parse("north, west"))
        assertNull(GeoPoint.parse("91, 0"))
        assertNull(GeoPoint.parse("0, 181"))
    }

    @Test
    fun format_roundTripsThroughParse() {
        val p = GeoPoint(45.50170, -73.56730)
        assertEquals(p, GeoPoint.parse(p.format()))
    }
}
