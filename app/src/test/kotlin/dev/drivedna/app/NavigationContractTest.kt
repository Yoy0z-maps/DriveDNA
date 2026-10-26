package dev.drivedna.app

import dev.drivedna.app.navigation.Destination
import dev.drivedna.app.ui.formatDistance
import dev.drivedna.app.ui.formatScore
import dev.drivedna.app.ui.components.badgeLabel
import dev.drivedna.core.domain.model.DataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationContractTest {
    @Test
    fun allEightScreensExist_withUniqueRoutes() {
        assertEquals(8, Destination.entries.size)
        assertEquals(8, Destination.entries.map { it.route }.toSet().size)
    }

    @Test
    fun analysisAndDataManagementScreens_areLockedWhileDriving() {
        val locked = Destination.entries.filter { it.detailed }.toSet()
        assertTrue(locked.containsAll(listOf(Destination.TripResult, Destination.MyDna, Destination.Settings, Destination.TripReplay)))
    }

    @Test
    fun badgeLabels_areDistinctAndSayDemoForMock() {
        assertTrue(DataSource.MOCK.badgeLabel().contains("DEMO"))
        assertTrue(DataSource.LIVE.badgeLabel().contains("LIVE"))
    }

    @Test
    fun tripResultRoute_carriesId() {
        assertEquals("s05_trip_result/42", Destination.tripResult(42))
    }

    @Test
    fun missingValues_neverRenderAsZero() {
        assertEquals("점수 없음", formatScore(null))
        assertEquals("측정 불가", formatDistance(null))
        assertEquals("0점", formatScore(0))
    }
}
