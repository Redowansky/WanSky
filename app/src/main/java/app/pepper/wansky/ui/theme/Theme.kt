package app.pepper.wansky.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Hamyra's look: very dark navy background + bright cyan accent
 *  (v0.1.0's green theme was the wrong reference). */
private val Cyan = Color(0xFF29B6F6)
private val NavyBg = Color(0xFF0A0E18)
private val NavySurface = Color(0xFF121826)
private val NavyCard = Color(0xFF1A2233)

private val WanskyColors = darkColorScheme(
    primary = Cyan,
    onPrimary = Color.Black,
    secondary = Cyan,
    background = NavyBg,
    surface = NavySurface,
    surfaceVariant = NavyCard,
    onBackground = Color.White,
    onSurface = Color.White,
    error = Color(0xFFEF5350)
)

@Composable
fun WanskyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WanskyColors, content = content)
}
