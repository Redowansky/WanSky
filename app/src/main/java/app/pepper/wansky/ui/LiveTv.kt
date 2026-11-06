package app.pepper.wansky.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.pepper.wansky.data.Playlists
import coil.compose.AsyncImage

/** The white circular channel logo used by the Live Now row and the
 *  channel list (logo when the playlist has one, else the initial). */
@Composable
fun ChannelLogo(c: Playlists.Channel, size: Dp) {
    Box(
        Modifier.size(size).background(Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!c.logo.isNullOrBlank()) {
            AsyncImage(
                model = c.logo,
                contentDescription = c.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size - 12.dp).clip(CircleShape)
            )
        } else {
            Text(
                c.name.firstOrNull()?.toString() ?: "?",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

/** Channel list content, shared by the Home top tabs (Live TV / Sports).
 *  The caller owns loading — the lists are fetched once by HomeScreen. */
@Composable
fun ChannelListContent(
    nav: NavController,
    channels: List<Playlists.Channel>,
    loading: Boolean,
    errors: List<String>,
    showSearch: Boolean = true,
    header: String? = null,
    onReload: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("All") }

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
        if (header != null) {
            Text(header, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
        }
        if (showSearch) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search channels") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onReload) {
                    Icon(Icons.Filled.Refresh, "Reload lists")
                }
            }
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
        }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            if (errors.isNotEmpty()) {
                Text(
                    errors.joinToString("\n"),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
            }
            Text("${shown.size} channels", style = MaterialTheme.typography.bodySmall)
            if (shown.isEmpty()) {
                Text("No channels here yet.")
            } else {
                LazyColumn {
                    items(shown) { c ->
                        ChannelRow(c) { playChannel(nav, c) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(c: Playlists.Channel, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelLogo(c, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                c.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${c.group} • ${c.source}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
    HorizontalDivider()
}
