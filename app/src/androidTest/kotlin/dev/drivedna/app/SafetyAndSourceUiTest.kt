package dev.drivedna.app

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.drivedna.app.ui.components.RestrictedContent
import dev.drivedna.app.ui.components.SourceBadge
import dev.drivedna.core.domain.model.DataSource
import dev.drivedna.core.domain.model.DrivingRestriction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafetyAndSourceUiTest {
    @get:Rule
    val compose = createComposeRule()

    /**
     * On AAOS the test activity is hosted in the launcher's TaskView and can attach late, which
     * made "No compose hierarchies found" flaky (Phase 4). Wait for the root before asserting.
     */
    private fun show(content: @Composable () -> Unit) {
        compose.setContent(content)
        compose.waitUntil(timeoutMillis = 10_000) {
            runCatching { compose.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
    }

    private fun showGate(restriction: DrivingRestriction) = show { RestrictedContent(restriction) { Text("DETAIL") } }

    @Test
    fun t09_restricted_hidesDetail() {
        showGate(DrivingRestriction.RESTRICTED)
        compose.onNodeWithText("DETAIL").assertDoesNotExist()
        compose.onNodeWithText("주행 중에는 상세 화면을 볼 수 없어요").assertIsDisplayed()
    }

    @Test
    fun t09_unknown_hidesDetail() {
        showGate(DrivingRestriction.UNKNOWN)
        compose.onNodeWithText("DETAIL").assertDoesNotExist()
    }

    @Test
    fun t09_parked_showsDetail() {
        showGate(DrivingRestriction.UNRESTRICTED)
        compose.onNodeWithText("DETAIL").assertIsDisplayed()
    }

    @Test
    fun t11_mockBadge_saysDemo() {
        show { SourceBadge(DataSource.MOCK) }
        compose.onNodeWithText("DEMO · SIMULATED").assertIsDisplayed()
        compose.onNodeWithText("LIVE · VEHICLE").assertDoesNotExist()
    }

    @Test
    fun t11_liveBadge_saysLive() {
        show { SourceBadge(DataSource.LIVE) }
        compose.onNodeWithText("LIVE · VEHICLE").assertIsDisplayed()
    }
}
