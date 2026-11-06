package app.pepper.wansky.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.pepper.wansky.data.Catalog
import app.pepper.wansky.data.Movie

/** Discover (bottom tab): search + a 2-column grid of everything. */
@Composable
fun DiscoverScreen(nav: NavController) {
    var movies by remember { mutableStateOf<List<Movie>?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            movies = Catalog.fetch()
        } catch (e: Exception) {
            movies = emptyList()
        }
    }

    val shown = remember(movies, query) {
        movies.orEmpty().filter {
            query.isBlank() || it.title.contains(query, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Discover", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search movies & series") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        if (movies == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (shown.isEmpty()) {
            Text("Nothing found for \"$query\".")
        } else {
            PosterGrid(shown) { nav.navigate("detail/${it.id}") }
        }
    }
}

/** "See all" / category screen: 2-column poster grid, like Hamyra's
 *  Hindi page (back arrow + title + search icon). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridScreen(nav: NavController) {
    val title = GridQueue.title
    val movies = GridQueue.movies
    val note = GridQueue.note
    var query by remember { mutableStateOf("") }

    val shown = remember(movies, query) {
        movies.filter {
            query.isBlank() || it.title.contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton({ nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { inner ->
        Column(Modifier.padding(inner).padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search this list") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
            }
            if (shown.isEmpty()) {
                Text("Nothing here yet.")
            } else {
                PosterGrid(shown) { nav.navigate("detail/${it.id}") }
            }
        }
    }
}

/** 2-column poster grid shared by Discover, Grid and My List. */
@Composable
fun PosterGrid(movies: List<Movie>, onOpen: (Movie) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(movies) { m ->
            PosterItem(m, Modifier.fillMaxWidth()) { onOpen(m) }
        }
    }
}
