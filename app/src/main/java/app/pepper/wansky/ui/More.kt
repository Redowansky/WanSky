package app.pepper.wansky.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.pepper.wansky.data.Catalog
import app.pepper.wansky.data.Favorites
import app.pepper.wansky.data.Movie

@Composable
fun YouTubeScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("YouTube", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "YouTube search and in-app play land in v0.2 — it needs a " +
                "YouTube Data API key. For now, your movies, series, live TV " +
                "and sports are all here."
        )
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
            LazyColumn {
                items(list) { m ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { nav.navigate("detail/${m.id}") }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(m.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOfNotNull(m.year, m.type).joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
