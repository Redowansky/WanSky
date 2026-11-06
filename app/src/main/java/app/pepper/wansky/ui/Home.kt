package app.pepper.wansky.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.pepper.wansky.data.Catalog
import app.pepper.wansky.data.Favorites
import app.pepper.wansky.data.Movie
import app.pepper.wansky.data.StreamGate
import app.pepper.wansky.data.isEmbedUrl
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/** Mood -> genre keywords. Picks come from his own catalog, so the
 *  results are thin while the library is small — and honest about it. */
private val MOODS = mapOf(
    "Feel-good" to listOf("Comedy", "Romance", "Family"),
    "Action night" to listOf("Action", "Thriller", "Crime"),
    "Something scary" to listOf("Horror", "Thriller"),
    "Mind-bending sci-fi" to listOf("Science Fiction", "Fantasy"),
    "Family night" to listOf("Family", "Animation")
)

@Composable
fun HomeScreen(nav: NavController) {
    var movies by remember { mutableStateOf<List<Movie>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            movies = Catalog.fetch()
        } catch (e: Exception) {
            error = e.message
        }
    }

    val list = movies.orEmpty()
    val filtered = remember(list, query, mood) {
        var out = list
        if (query.isNotBlank()) {
            out = out.filter { it.title.contains(query, ignoreCase = true) }
        }
        if (mood != null) {
            val want = MOODS[mood] ?: emptyList()
            out = out.filter { m ->
                m.genres.any { g -> want.any { w -> g.contains(w, ignoreCase = true) } }
            }
        }
        out
    }
    val moviesOnly = filtered.filter { it.type != "series" }
    val seriesOnly = filtered.filter { it.type == "series" }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Wansky",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search movies & series") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (error != null) {
            item { Text("Couldn't load the catalog: $error", color = MaterialTheme.colorScheme.error) }
        }
        if (movies == null && error == null) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        val hero = list.firstOrNull { it.backdropUrl() != null } ?: list.firstOrNull()
        if (hero != null && query.isBlank() && mood == null) {
            item { HeroCard(hero) { nav.navigate("detail/${hero.id}") } }
        }
        item {
            Text("AI Picks — what are you in the mood for?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MOODS.keys.toList()) { name ->
                    FilterChip(
                        selected = mood == name,
                        onClick = { mood = if (mood == name) null else name },
                        label = { Text(name) }
                    )
                }
            }
            if (mood != null && filtered.isEmpty()) {
                Text(
                    "Nothing in the catalog matches $mood yet — the library is still small.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (moviesOnly.isNotEmpty()) {
            item { Text("Movies", style = MaterialTheme.typography.titleLarge) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(moviesOnly) { PosterCard(it) { nav.navigate("detail/${it.id}") } }
                }
            }
        }
        if (seriesOnly.isNotEmpty()) {
            item { Text("TV Series", style = MaterialTheme.typography.titleLarge) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(seriesOnly) { PosterCard(it) { nav.navigate("detail/${it.id}") } }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun HeroCard(movie: Movie, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onClick() }) {
        Box {
            AsyncImage(
                model = movie.backdropUrl() ?: movie.posterUrl(),
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
            Box(
                Modifier.matchParentSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            )
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(movie.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    listOfNotNull(movie.year, movie.vote?.let { "★ $it" }).joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun PosterCard(movie: Movie, onClick: () -> Unit) {
    Column(Modifier.width(140.dp).clickable { onClick() }) {
        AsyncImage(
            model = movie.posterUrl(),
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.height(210.dp).fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            movie.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            listOfNotNull(movie.year, movie.vote?.let { "★ $it" }).joinToString(" • "),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(nav: NavController, id: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var movie by remember { mutableStateOf<Movie?>(null) }
    var fav by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var resolveError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(id) {
        try {
            movie = Catalog.fetch().find { it.id == id } ?: Catalog.byId(id)
        } catch (e: Exception) {
            // movie stays null; the screen shows a spinner
        }
        fav = Favorites.isFav(ctx, id)
    }

    fun play(url: String, label: String) {
        if (isEmbedUrl(url)) {
            PlayQueue.webUrl = url
            PlayQueue.title = label
            nav.navigate("webview")
            return
        }
        resolving = true
        resolveError = null
        scope.launch {
            val resolved = StreamGate.resolve(url)
            resolving = false
            if (resolved == null) {
                resolveError = "Couldn't start playback — check your connection and try again."
                return@launch
            }
            PlayQueue.url = resolved
            PlayQueue.title = label
            nav.navigate("player")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(movie?.title ?: "") },
                navigationIcon = {
                    IconButton({ nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton({
                        movie?.let { fav = Favorites.toggle(ctx, it.id) }
                    }) {
                        Icon(
                            if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            "Favorite"
                        )
                    }
                }
            )
        }
    ) { inner ->
        val m = movie
        if (m == null) {
            Box(Modifier.padding(inner).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                Modifier.padding(inner).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    AsyncImage(
                        model = m.backdropUrl() ?: m.posterUrl(),
                        contentDescription = m.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(220.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                item { Text(m.title, style = MaterialTheme.typography.headlineSmall) }
                item {
                    Text(
                        listOfNotNull(
                            m.year,
                            m.vote?.let { "★ $it" },
                            m.type.replaceFirstChar { it.uppercase() }
                        ).joinToString(" • ")
                    )
                }
                if (m.genres.isNotEmpty()) {
                    item { Text(m.genres.joinToString(" • "), style = MaterialTheme.typography.bodyMedium) }
                }
                m.overview?.let { item { Text(it) } }
                if (m.type == "series") {
                    item { Text("Episodes", style = MaterialTheme.typography.titleMedium) }
                }
                items(m.sources) { src ->
                    Button(
                        onClick = { play(src.url, "${m.title} — ${src.label}") },
                        enabled = !resolving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text(src.label)
                    }
                }
                if (resolving) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Starting playback…")
                        }
                    }
                }
                resolveError?.let { err ->
                    item { Text(err, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
