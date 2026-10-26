package dev.drivedna.core.domain

import dev.drivedna.core.domain.model.DrivingRestriction
import dev.drivedna.core.domain.model.Gear
import dev.drivedna.core.domain.model.gearFromAaos
import dev.drivedna.core.domain.model.metersPerSecondToKph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitsTest {
    @Test
    fun speedConversion_matchesProbeSample() {
        // 25.277779 m/s was observed for a 91 km/h reading in the Phase 1 probe.
        assertEquals(91.0, metersPerSecondToKph(25.277779f), 0.01)
        assertEquals(0.0, metersPerSecondToKph(0f), 0.0)
    }

    @Test
    fun gearMapping_coversEmulatorSupportedValues() {
        assertEquals(Gear.PARK, gearFromAaos(4))
        assertEquals(Gear.NEUTRAL, gearFromAaos(1))
        assertEquals(Gear.REVERSE, gearFromAaos(2))
        assertEquals(Gear.DRIVE, gearFromAaos(8))
        assertEquals(Gear.MANUAL, gearFromAaos(16))
        assertEquals(Gear.UNKNOWN, gearFromAaos(0))
    }

    @Test
    fun unknownRestriction_locksDetailedUi() {
        assertFalse(DrivingRestriction.UNKNOWN.allowsDetailedUi)
        assertFalse(DrivingRestriction.RESTRICTED.allowsDetailedUi)
        assertTrue(DrivingRestriction.UNRESTRICTED.allowsDetailedUi)
    }
}
