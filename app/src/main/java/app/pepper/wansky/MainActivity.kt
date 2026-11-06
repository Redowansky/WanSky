package app.pepper.wansky

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.pepper.wansky.ui.DetailScreen
import app.pepper.wansky.ui.HomeScreen
import app.pepper.wansky.ui.LiveTvScreen
import app.pepper.wansky.ui.MyListScreen
import app.pepper.wansky.ui.PlayerScreen
import app.pepper.wansky.ui.SportsScreen
import app.pepper.wansky.ui.WebViewPlayerScreen
import app.pepper.wansky.ui.YouTubeScreen
import app.pepper.wansky.ui.theme.WanskyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WanskyTheme {
                val nav = rememberNavController()
                val backStack by nav.currentBackStackEntryAsState()
                val route = backStack?.destination?.route
                val showNav = route in listOf("home", "livetv", "sports", "youtube", "mylist")
                Scaffold(
                    bottomBar = {
                        if (showNav) {
                            NavigationBar {
                                navItem(nav, route, "home", "Home", Icons.Filled.Home)
                                navItem(nav, route, "livetv", "Live TV", Icons.Filled.LiveTv)
                                navItem(nav, route, "sports", "Sports", Icons.Filled.SportsSoccer)
                                navItem(nav, route, "youtube", "YouTube", Icons.Filled.PlayArrow)
                                navItem(nav, route, "mylist", "My List", Icons.Filled.Favorite)
                            }
                        }
                    }
                ) { inner ->
                    NavHost(nav, startDestination = "home", Modifier.padding(inner)) {
                        composable("home") { HomeScreen(nav) }
                        composable("livetv") { LiveTvScreen(nav) }
                        composable("sports") { SportsScreen(nav) }
                        composable("youtube") { YouTubeScreen() }
                        composable("mylist") { MyListScreen(nav) }
                        composable("detail/{id}") { e ->
                            DetailScreen(nav, e.arguments?.getString("id") ?: "")
                        }
                        composable("player") { PlayerScreen(nav) }
                        composable("webview") { WebViewPlayerScreen(nav) }
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
