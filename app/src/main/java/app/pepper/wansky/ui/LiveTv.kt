package app.pepper.wansky.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.pepper.wansky.data.Playlists
import kotlinx.coroutines.launch

@Composable
fun LiveTvScreen(nav: NavController) {
    var result by remember { mutableStateOf<Playlists.LoadResult?>(null) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("All") }
    val scope = rememberCoroutineScope()

    suspend fun load(force: Boolean = false) {
        loading = true
        result = try {
            Playlists.loadAll(force)
        } catch (e: Exception) {
            Playlists.LoadResult(emptyList(), listOf(e.message ?: "Load failed"))
        }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    val channels = result?.channels.orEmpty()
    val groups = remember(channels) {
        listOf("All") + channels.groupingBy { it.group }.eachCount()
            .entries.sortedByDescending { it.value }.take(14).map { it.key }
    }
    val shown = remember(channels, query, group) {
        channels.filter {
            (group == "All" || it.group == group) &&
                (query.isBlank() || it.name.contains(query, ignoreCase = true))
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Live TV",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton({ scope.launch { load(true) } }) {
                Icon(Icons.Filled.Refresh, "Reload lists")
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search channels") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        LazyRow {
            items(groups) { g ->
                FilterChip(
                    selected = group == g,
                    onClick = { group = g },
                    label = { Text(g) },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            if (result?.errors?.isNotEmpty() == true) {
                Text(
                    result!!.errors.joinToString("\n"),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "${shown.size} channels",
                style = MaterialTheme.typography.bodySmall
            )
            ChannelList(shown, nav)
        }
    }
}

@Composable
fun SportsScreen(nav: NavController) {
    var result by remember { mutableStateOf<Playlists.LoadResult?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        result = try {
            Playlists.loadAll()
        } catch (e: Exception) {
            Playlists.LoadResult(emptyList(), listOf(e.message ?: "Load failed"))
        }
        loading = false
    }

    val sports = remember(result) {
        result?.channels.orEmpty().filter { Playlists.isSports(it) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Sports", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Live sports channels from your playlists",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            ChannelList(sports, nav)
        }
    }
}

@Composable
private fun ChannelList(channels: List<Playlists.Channel>, nav: NavController) {
    if (channels.isEmpty()) {
        Text("No channels here yet.")
    } else {
        LazyColumn {
            items(channels) { c ->
                ChannelRow(c) {
                    PlayQueue.url = c.url
                    PlayQueue.title = c.name
                    nav.navigate("player")
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(c: Playlists.Channel, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp)
    ) {
        Text(c.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            "${c.group} • ${c.source}",
            style = MaterialTheme.typography.bodySmall
        )
    }
    HorizontalDivider()
}
