package dev.drivedna.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

// Car displays: large type and high contrast (04_UI_UX_SPEC §1).
private val CarTypography = Typography(
    headlineLarge = TextStyle(fontSize = 40.sp, lineHeight = 48.sp),
    titleLarge = TextStyle(fontSize = 30.sp, lineHeight = 38.sp),
    bodyLarge = TextStyle(fontSize = 24.sp, lineHeight = 32.sp),
    labelLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp),
)

@Composable
fun DriveDnaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), typography = CarTypography, content = content)
}
