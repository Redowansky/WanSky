package app.pepper.wansky

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.pepper.wansky.ui.CrashTrap
import app.pepper.wansky.ui.DetailScreen
import app.pepper.wansky.ui.DiscoverScreen
import app.pepper.wansky.ui.GridScreen
import app.pepper.wansky.ui.HomeScreen
import app.pepper.wansky.ui.MyListScreen
import app.pepper.wansky.ui.PlayerScreen
import app.pepper.wansky.ui.WebViewPlayerScreen
import app.pepper.wansky.ui.YouTubeScreen
import app.pepper.wansky.ui.YtPlayerScreen
import app.pepper.wansky.ui.theme.WanskyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashTrap.install(this)
        setContent {
            WanskyTheme {
                val ctx = LocalContext.current
                val nav = rememberNavController()
                val backStack by nav.currentBackStackEntryAsState()
                val route = backStack?.destination?.route
                // Hamyra's bottom nav: Home / Discover / My List / YouTube.
                val showNav = route in listOf("home", "discover", "mylist", "youtube")
                var crashLog by remember { mutableStateOf(CrashTrap.read(ctx)) }

                if (crashLog != null) {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("Wansky crashed last time") },
                        text = {
                            Text(
                                "Copy this and send it to Pepper — it says exactly " +
                                    "where the crash happened:\n\n" + crashLog!!,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(320.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        },
                        confirmButton = {
                            TextButton({
                                val cm =
                                    ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(
                                    ClipData.newPlainText("Wansky crash log", crashLog)
                                )
                            }) { Text("Copy") }
                        },
                        dismissButton = {
                            TextButton({
                                CrashTrap.clear(ctx)
                                crashLog = null
                            }) { Text("Dismiss") }
                        }
                    )
                }

                Scaffold(
                    bottomBar = {
                        if (showNav) {
                            NavigationBar {
                                navItem(nav, route, "home", "Home", Icons.Filled.Home)
                                navItem(nav, route, "discover", "Discover", Icons.Filled.Search)
                                navItem(nav, route, "mylist", "My List", Icons.Filled.Bookmark)
                                navItem(nav, route, "youtube", "YouTube", Icons.Filled.PlayCircle)
                            }
                        }
                    }
                ) { inner ->
                    NavHost(nav, startDestination = "home", Modifier.padding(inner)) {
                        composable("home") { HomeScreen(nav) }
                        composable("discover") { DiscoverScreen(nav) }
                        composable("mylist") { MyListScreen(nav) }
                        composable("youtube") { YouTubeScreen(nav) }
                        composable("grid") { GridScreen(nav) }
                        composable("detail/{id}") { e ->
                            DetailScreen(nav, e.arguments?.getString("id") ?: "")
                        }
                        composable("player") { PlayerScreen(nav) }
                        composable("webview") { WebViewPlayerScreen(nav) }
                        composable("ytplayer/{id}") { e ->
                            YtPlayerScreen(nav, e.arguments?.getString("id") ?: "")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.navItem(
    nav: NavController,
    current: String?,
    route: String,
    label: String,
    icon: ImageVector
) {
    NavigationBarItem(
        selected = current == route,
        onClick = {
            nav.navigate(route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        icon = { Icon(icon, label) },
        label = { Text(label) }
    )
}
