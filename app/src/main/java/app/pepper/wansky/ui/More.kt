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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.pepper.wansky.data.Catalog
import app.pepper.wansky.data.Favorites
import app.pepper.wansky.data.Movie
import app.pepper.wansky.data.YouTube
import coil.compose.AsyncImage

/** YouTube tab (v0.2): Hamyra's layout — top bar, chips, video cards —
 *  now backed by the real YouTube Data API v3. First load shows the
 *  Bangladesh trending feed (cheap: 1 quota unit); searching costs
 *  100 units, so the feed is the default. Tapping a video opens the
 *  in-app embed player with an Up next list. */
@Composable
fun YouTubeScreen(nav: NavController) {
    val ctx = LocalContext.current
    var chip by remember { mutableStateOf(YouTube.Filter.ALL) }
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var videos by remember { mutableStateOf<List<YouTube.Video>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Bumped on every (re)load so the effect below re-runs.
    var reloadKey by remember { mutableStateOf(0) }

    fun load() {
        loading = true
        error = null
    }

    LaunchedEffect(reloadKey) {
        loading = true
        error = null
        try {
            videos = if (submitted.isBlank() && chip == YouTube.Filter.ALL) {
                YouTube.trending()
            } else {
                YouTube.search(submitted, chip)
            }
        } catch (e: Exception) {
            error = YouTube.friendlyError(e)
            videos = emptyList()
        }
        loading = false
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
            IconButton({
                Toast.makeText(ctx, "YouTube settings live in v0.2's search — nothing here yet", Toast.LENGTH_SHORT).show()
            }) { Icon(Icons.Filled.Settings, "Settings") }
        }
        // Search field.
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search YouTube…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                submitted = query.trim()
                reloadKey++
            }),
            trailingIcon = {
                IconButton({
                    submitted = query.trim()
                    reloadKey++
                }) { Icon(Icons.Filled.Search, "Search") }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(YouTube.Filter.values().toList()) { f ->
                val active = f == chip
                Box(
                    Modifier
                        .background(
                            if (active) Color.White else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            chip = f
                            reloadKey++
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        f.label,
                        color = if (active) Color.Black else Color.White,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("YouTube isn't loading", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                            .clickable { load(); reloadKey++ }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) { Text("Try again", fontWeight = FontWeight.Bold) }
                }
            }
            videos.isNullOrEmpty() -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No videos found — try another search.")
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(videos!!, key = { it.id }) { v ->
                        YtVideoCard(v) {
                            YtQueue.title = v.title
                            nav.navigate("ytplayer/${v.id}")
                        }
                    }
                }
            }
        }
    }
}

/** Hamyra-style video card: 16:9 thumb with a duration badge, then a
 *  channel initial, title (2 lines) and channel • views • age. Shared
 *  by the feed and the player's Up next list. */
@Composable
fun YtVideoCard(v: YouTube.Video, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }) {
        Box(Modifier.fillMaxWidth()) {
            AsyncImage(
                model = v.thumb,
                contentDescription = v.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
            )
            if (v.duration.isNotBlank()) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(v.duration, color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    v.channel.firstOrNull()?.uppercase() ?: "▶",
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    v.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val meta = listOf(v.channel, v.views, v.published)
                    .filter { it.isNotBlank() }.joinToString(" • ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
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
