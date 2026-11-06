package app.pepper.wansky.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.pepper.wansky.data.Catalog
import app.pepper.wansky.data.Favorites
import app.pepper.wansky.data.Movie

/** YouTube tab: Hamyra's layout (top bar, chips, video cards). The feed
 *  itself needs a YouTube Data API key — that lands in v0.2, so this
 *  build shows the layout with an honest empty state. */
@Composable
fun YouTubeScreen() {
    val ctx = LocalContext.current
    var chip by remember { mutableStateOf("All") }
    val chips = listOf("All", "Shorts", "Music", "Sports", "News")
    val notYet = {
        Toast.makeText(
            ctx,
            "YouTube needs the Data API key — coming in v0.2",
            Toast.LENGTH_SHORT
        ).show()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(32.dp)
                    .background(Color(0xFFFF0000), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "YouTube",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(notYet) { Icon(Icons.Filled.Search, "Search") }
            IconButton(notYet) { Icon(Icons.Filled.Settings, "Settings") }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chips) { c ->
                val active = c == chip
                Box(
                    Modifier
                        .background(
                            if (active) Color.White else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { chip = c }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        c,
                        color = if (active) Color.Black else Color.White,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "YouTube is coming in v0.2",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Search and in-app play need a YouTube Data API key. " +
                    "For now, your movies, series, live TV and sports " +
                    "are all on the other tabs.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun MyListScreen(nav: NavController) {
    val ctx = LocalContext.current
    var movies by remember { mutableStateOf<List<Movie>?>(null) }

    LaunchedEffect(Unit) {
        try {
            val all = Catalog.fetch()
            val favs = Favorites.ids(ctx)
            movies = all.filter { favs.contains(it.id) }
        } catch (e: Exception) {
            movies = emptyList()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("My List", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        val list = movies
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (list.isEmpty()) {
            Text("Nothing saved yet — tap the ♡ on any movie or series to add it here.")
        } else {
            PosterGrid(list) { nav.navigate("detail/${it.id}") }
        }
    }
}
