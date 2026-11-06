package app.pepper.wansky.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.pepper.wansky.data.Catalog
import app.pepper.wansky.data.Favorites
import app.pepper.wansky.data.Movie
import app.pepper.wansky.data.Playlists
import app.pepper.wansky.data.StreamGate
import app.pepper.wansky.data.isEmbedUrl
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Handoff for the grid screen: what to show. */
object GridQueue {
    var title: String = ""
    var movies: List<Movie> = emptyList()
    var note: String? = null
}

private val TOP_TABS = listOf(
    "All", "Live TV", "Sports", "Drama", "Movies", "Series", "AI Picks"
)

@Composable
fun HomeScreen(nav: NavController) {
    var movies by remember { mutableStateOf<List<Movie>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var channels by remember { mutableStateOf<List<Playlists.Channel>>(emptyList()) }
    var channelErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    var channelsLoading by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf("All") }
    var info by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            movies = Catalog.fetch()
        } catch (e: Exception) {
            error = e.message
        }
    }
    // Playlists load once for the whole Home screen (Live Now row +
    // Live TV / Sports tabs share the result). reloadKey re-runs it.
    LaunchedEffect(reloadKey) {
        val r = try {
            Playlists.loadAll()
        } catch (e: Exception) {
            Playlists.LoadResult(emptyList(), listOf(e.message ?: "Load failed"))
        }
        channels = r.channels
        channelErrors = r.errors
        channelsLoading = false
    }

    if (info != null) {
        AlertDialog(
            onDismissRequest = { info = null },
            confirmButton = {
                TextButton({ info = null }) { Text("OK") }
            },
            text = { Text(info!!) }
        )
    }

    fun openGrid(title: String, list: List<Movie>, note: String? = null) {
        GridQueue.title = title
        GridQueue.movies = list
        GridQueue.note = note
        nav.navigate("grid")
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            onBell = {
                info = "Notifications aren't set up yet — episode alerts " +
                    "are planned for a later build."
            },
            onSettings = { info = "Settings are coming in a later build." }
        )
        TopTabs(tab) { tab = it }
        when (tab) {
            "All" -> HomeAllTab(
                nav = nav,
                movies = movies,
                error = error,
                channels = channels,
                channelsLoading = channelsLoading,
                channelErrors = channelErrors,
                onTab = { tab = it },
                onOpenGrid = ::openGrid
            )
            "Live TV" -> ChannelListContent(
                nav, channels, channelsLoading, channelErrors,
                showSearch = true,
                onReload = { reloadKey++ }
            )
            "Sports" -> ChannelListContent(
                nav,
                channels.filter { Playlists.isSports(it) },
                channelsLoading,
                channelErrors,
                showSearch = false,
                header = "Live sports channels from your playlists",
                onReload = { reloadKey++ }
            )
            "Drama" -> CatalogSection(nav, movies, error, "Drama") { m ->
                m.genres.any { it.contains("Drama", ignoreCase = true) }
            }
            "Movies" -> CatalogSection(nav, movies, error, "Movies") { m ->
                m.type != "series"
            }
            "Series" -> CatalogSection(nav, movies, error, "Series") { m ->
                m.type == "series"
            }
            "AI Picks" -> AiPicksScreen(nav, movies.orEmpty())
        }
    }
}

@Composable
private fun TopBar(onBell: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(32.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "Wansky",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        IconButton(onBell) { Icon(Icons.Filled.Notifications, "Notifications") }
        IconButton(onSettings) { Icon(Icons.Filled.Settings, "Settings") }
    }
}

@Composable
private fun TopTabs(selected: String, onSelect: (String) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(TOP_TABS) { t ->
            val active = t == selected
            Column(
                Modifier.clickable { onSelect(t) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    t,
                    color = if (active) Color.White else Color.Gray,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.width(28.dp).height(3.dp)
                        .alpha(if (active) 1f else 0f)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF29B6F6), Color(0xFFF472B6))
                            ),
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun HomeAllTab(
    nav: NavController,
    movies: List<Movie>?,
    error: String?,
    channels: List<Playlists.Channel>,
    channelsLoading: Boolean,
    channelErrors: List<String>,
    onTab: (String) -> Unit,
    onOpenGrid: (String, List<Movie>, String?) -> Unit
) {
    val scope = rememberCoroutineScope()
    var playError by remember { mutableStateOf<String?>(null) }
    val list = movies.orEmpty()
    // Computed here, in @Composable context — a LazyColumn's content
    // lambda is NOT @Composable, so remember() may not be called there.
    val heroes = remember(list) {
        list.filter { it.backdropUrl() != null }.take(5)
            .ifEmpty { list.take(5) }
    }
    val latest = remember(list) { list.sortedByDescending { it.year ?: "" }.take(10) }
    val moviesOnly = list.filter { it.type != "series" }
    val seriesOnly = list.filter { it.type == "series" }
    val hindi = list.filter { it.language() == "Hindi" }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        if (error != null) {
            item {
                Text(
                    "Couldn't load the catalog: $error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        if (movies == null && error == null) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        if (heroes.isNotEmpty()) {
            item {
                HeroCarousel(
                    heroes,
                    onPlay = { m ->
                        playMovie(scope, nav, m, onError = { playError = it })
                    },
                    onOpen = { m -> nav.navigate("detail/${m.id}") }
                )
            }
        }
        item {
            CategoryChips(
                hindiBackdrop = list.firstOrNull { it.language() == "Hindi" }?.backdropUrl(),
                onAll = { onOpenGrid("All", list, null) },
                onHindi = {
                    onOpenGrid("Hindi", list.filter { it.language() == "Hindi" }, null)
                },
                onBengali = { onTab("Live TV") }
            )
        }
        item {
            SourceRow(
                onLibrary = { onOpenGrid("All", list, null) },
                onLiveTv = { onTab("Live TV") },
                onSports = { onTab("Sports") },
                onYouTube = { nav.navigate("youtube") }
            )
        }
        item {
            LiveNowRow(
                channels, channelsLoading, channelErrors,
                onViewAll = { onTab("Live TV") },
                onPlay = { playChannel(nav, it) }
            )
        }
        if (latest.isNotEmpty()) {
            item {
                ContentRow("Latest", latest,
                    onSeeAll = { onOpenGrid("Latest", latest, null) },
                    onOpen = { nav.navigate("detail/${it.id}") })
            }
        }
        if (moviesOnly.isNotEmpty()) {
            item {
                ContentRow("Movies", moviesOnly,
                    onSeeAll = { onOpenGrid("Movies", moviesOnly, null) },
                    onOpen = { nav.navigate("detail/${it.id}") })
            }
        }
        if (seriesOnly.isNotEmpty()) {
            item {
                ContentRow("TV Series", seriesOnly,
                    onSeeAll = { onOpenGrid("TV Series", seriesOnly, null) },
                    onOpen = { nav.navigate("detail/${it.id}") })
            }
        }
        if (hindi.isNotEmpty()) {
            item {
                ContentRow("Hindi Dubbed", hindi,
                    onSeeAll = { onOpenGrid("Hindi Dubbed", hindi, null) },
                    onOpen = { nav.navigate("detail/${it.id}") })
            }
        }
        if (playError != null) {
            item {
                Text(
                    playError!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun HeroCarousel(
    heroes: List<Movie>,
    onPlay: (Movie) -> Unit,
    onOpen: (Movie) -> Unit
) {
    var index by remember { mutableStateOf(0) }
    val count = heroes.size
    LaunchedEffect(count) {
        while (count > 1) {
            delay(5000)
            index = (index + 1) % count
        }
    }
    val hero = heroes[index % count]
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clickable { onOpen(hero) },
        shape = RoundedCornerShape(20.dp)
    ) {
        Box {
            AsyncImage(
                model = hero.backdropUrl() ?: hero.posterUrl(),
                contentDescription = hero.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                        startY = 90f
                    )
                )
            )
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(
                    hero.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(
                        hero.vote?.let { "★ $it" },
                        hero.year
                    ).joinToString("  "),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onPlay(hero) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.Black)
                    Spacer(Modifier.width(4.dp))
                    Text("Play", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
            Row(
                Modifier.align(Alignment.BottomEnd).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                repeat(count) { i ->
                    Box(
                        Modifier.size(if (i == index % count) 8.dp else 6.dp)
                            .background(
                                if (i == index % count) Color.White
                                else Color.White.copy(alpha = 0.45f),
                                CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChips(
    hindiBackdrop: String?,
    onAll: () -> Unit,
    onHindi: () -> Unit,
    onBengali: () -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            "Categories",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryChip(
                "All",
                Brush.linearGradient(listOf(Color(0xFF1D4ED8), Color(0xFF7C3AED))),
                null,
                Modifier.weight(1f),
                onAll
            )
            CategoryChip(
                "Bengali",
                Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF065F46))),
                null,
                Modifier.weight(1f),
                onBengali
            )
            CategoryChip(
                "Hindi",
                Brush.linearGradient(listOf(Color(0xFFB45309), Color(0xFFDC2626))),
                hindiBackdrop,
                Modifier.weight(1f),
                onHindi
            )
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    brush: Brush,
    imageUrl: String?,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier.height(64.dp)
            .background(brush, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            Box(
                Modifier.matchParentSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
        }
        Text(
            label,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun SourceRow(
    onLibrary: () -> Unit,
    onLiveTv: () -> Unit,
    onSports: () -> Unit,
    onYouTube: () -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            "Browse by Source",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SourceTile("Library", Icons.Filled.Movie, Modifier.weight(1f), onLibrary)
            SourceTile("Live TV", Icons.Filled.Tv, Modifier.weight(1f), onLiveTv)
            SourceTile("Sports", Icons.Filled.SportsSoccer, Modifier.weight(1f), onSports)
            SourceTile("YouTube", Icons.Filled.PlayArrow, Modifier.weight(1f), onYouTube)
        }
    }
}

@Composable
private fun SourceTile(
    label: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(64.dp).background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(16.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

@Composable
private fun LiveNowRow(
    channels: List<Playlists.Channel>,
    loading: Boolean,
    errors: List<String>,
    onViewAll: () -> Unit,
    onPlay: (Playlists.Channel) -> Unit
) {
    val bangla = remember(channels) {
        channels.filter { c ->
            val n = c.name.lowercase()
            c.source == "Bdix" || "bangla" in n || "bangladesh" in n ||
                "channel i" in n || "ekattor" in n || "ntv" in n
        }.take(10)
    }
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "🔴 Live Now",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "View All ›",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onViewAll() }
            )
        }
        Spacer(Modifier.height(10.dp))
        when {
            loading -> Box(
                Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(Modifier.size(28.dp)) }
            bangla.isEmpty() -> Text(
                if (errors.isNotEmpty()) "Live lists failed to load."
                else "No live channels right now.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            else -> LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(bangla) { c ->
                    Column(
                        Modifier.width(64.dp).clickable { onPlay(c) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ChannelLogo(c, 56.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            c.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentRow(
    title: String,
    items: List<Movie>,
    onSeeAll: () -> Unit,
    onOpen: (Movie) -> Unit
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "See all ›",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onSeeAll() }
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { m ->
                PosterItem(m, Modifier.width(140.dp)) { onOpen(m) }
            }
        }
    }
}

/** Poster + title + year, with a Hindi/English language badge. */
@Composable
fun PosterItem(movie: Movie, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable { onClick() }) {
        Box {
            AsyncImage(
                model = movie.posterUrl(),
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.66f)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp)
                    )
                    .clip(RoundedCornerShape(12.dp))
            )
            movie.language()?.let { lang ->
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp)
                        .background(
                            Color.Black.copy(alpha = 0.65f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        lang,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            movie.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            listOfNotNull(movie.year, movie.vote?.let { "★ $it" }).joinToString(" • "),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
private fun CatalogSection(
    nav: NavController,
    movies: List<Movie>?,
    error: String?,
    title: String,
    filter: (Movie) -> Boolean
) {
    val list = movies.orEmpty().filter(filter)
    val scope = rememberCoroutineScope()
    var playError by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        if (error != null) {
            item {
                Text(
                    "Couldn't load the catalog: $error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        if (movies == null && error == null) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        val hero = list.firstOrNull { it.backdropUrl() != null } ?: list.firstOrNull()
        if (hero != null) {
            item {
                HeroCarousel(
                    listOf(hero),
                    onPlay = { m ->
                        playMovie(scope, nav, m, onError = { playError = it })
                    },
                    onOpen = { m -> nav.navigate("detail/${m.id}") }
                )
            }
        }
        if (list.isEmpty() && movies != null) {
            item {
                Text(
                    "No $title in the catalog yet.",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        if (list.isNotEmpty()) {
            item {
                ContentRow(title, list,
                    onSeeAll = {
                        GridQueue.title = title
                        GridQueue.movies = list
                        GridQueue.note = null
                        nav.navigate("grid")
                    },
                    onOpen = { nav.navigate("detail/${it.id}") })
            }
        }
        if (playError != null) {
            item {
                Text(
                    playError!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
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
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
                item {
                    Text(m.title, style = MaterialTheme.typography.headlineSmall)
                }
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
                    item {
                        Text(
                            m.genres.joinToString(" • "),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                m.overview?.let { item { Text(it) } }
                if (m.type == "series") {
                    item {
                        Text("Episodes", style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(m.sources) { src ->
                    Button(
                        onClick = { play(src.url, "${m.title} — ${src.label}") },
                        enabled = !resolving,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text(src.label, color = Color.Black, fontWeight = FontWeight.Bold)
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
