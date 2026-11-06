package app.pepper.wansky.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF22C55E)
private val LightGreen = Color(0xFF86EFAC)
private val DarkBg = Color(0xFF0A0F0B)
private val DarkSurface = Color(0xFF131A14)

private val WanskyColors = darkColorScheme(
    primary = Green,
    onPrimary = Color.Black,
    secondary = LightGreen,
    background = DarkBg,
    surface = DarkSurface,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun WanskyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WanskyColors, content = content)
}
