package dev.drivedna.core.data

import dev.drivedna.core.data.db.toDomain
import dev.drivedna.core.data.db.toEntity
import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.model.Gear
import dev.drivedna.core.domain.model.QualityFlag
import dev.drivedna.core.domain.model.VehicleSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {
    @Test
    fun sample_roundTripsIncludingNullsAndFlags() {
        val sample = VehicleSample(
            tripId = 7,
            timestampMs = 1_000,
            speedKph = null,
            brakePressed = true,
            gear = Gear.DRIVE,
            source = DataSource.MOCK,
            qualityFlags = setOf(QualityFlag.MISSING_VALUE, QualityFlag.GAP_TOO_LONG),
        )
        val back = sample.toEntity().toDomain()
        assertEquals(sample, back)
        assertNull("missing speed must stay null, not 0", back.speedKph)
    }

    @Test
    fun sample_withoutFlags_roundTrips() {
        val sample = VehicleSample(1, 2, 50.0, null, null, DataSource.LIVE)
        assertEquals(sample, sample.toEntity().toDomain())
    }
}
