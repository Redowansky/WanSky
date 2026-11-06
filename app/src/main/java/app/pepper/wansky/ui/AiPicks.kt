package app.pepper.wansky.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.pepper.wansky.data.Movie

/** Quick picks, same labels as Hamyra's screen, mapped onto his catalog. */
private val QUICK_PICKS = mapOf(
    "Feel-good movie night" to listOf("Comedy", "Romance", "Family"),
    "Mind-bending sci-fi" to listOf("Science Fiction", "Fantasy"),
    "Something scary" to listOf("Horror", "Thriller"),
    "Korean romance series" to listOf("Romance"),
    "Best movies of 2026" to listOf("__YEAR_2026__"),
    "Epic adventure movies" to listOf("Adventure", "Action")
)

/** Free-text mood words -> genres. */
private fun genresFor(text: String): List<String> {
    val t = text.lowercase()
    val out = mutableListOf<String>()
    if ("action" in t || "fight" in t) out += "Action"
    if ("scary" in t || "horror" in t || "ghost" in t) out += "Horror"
    if ("sci-fi" in t || "science" in t || "space" in t || "mind" in t) out += "Science Fiction"
    if ("romance" in t || "love" in t || "korean" in t) out += "Romance"
    if ("comedy" in t || "funny" in t || "laugh" in t) out += "Comedy"
    if ("family" in t || "kids" in t || "children" in t) out += "Family"
    if ("thriller" in t || "crime" in t || "mystery" in t) out += "Thriller"
    if ("drama" in t) out += "Drama"
    if ("fantasy" in t || "magic" in t) out += "Fantasy"
    if ("adventure" in t || "epic" in t) out += "Adventure"
    if ("2026" in t || "best" in t || "new" in t) out += "__YEAR_2026__"
    return out
}

private fun matches(movie: Movie, want: List<String>): Boolean {
    if (want.isEmpty()) return false
    return want.any { w ->
        if (w == "__YEAR_2026__") movie.year == "2026"
        else movie.genres.any { g -> g.contains(w, ignoreCase = true) }
    }
}

@Composable
fun AiPicksScreen(nav: NavController, movies: List<Movie>) {
    var input by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<String?>(null) }
    var typed by remember { mutableStateOf<String?>(null) }

    val results = remember(picked, typed, movies) {
        val want = when {
            picked != null -> QUICK_PICKS[picked] ?: emptyList()
            typed != null -> genresFor(typed!!)
            else -> emptyList()
        }
        movies.filter { matches(it, want) }
    }
    val activeLabel = picked ?: typed

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // The big intro card.
        item {
            Box(
                Modifier.fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(20.dp)
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Tell me what you're in the mood for",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "AI-picked movies & series, verified playable on Wansky.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }

        item {
            Text(
                "QUICK PICKS",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
        }
        // 2-column quick-pick cards, built as rows (no nested lazy list).
        items(QUICK_PICKS.keys.toList().chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { label ->
                    Box(
                        Modifier.weight(1f).height(72.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                picked = label
                                typed = null
                            }
                            .padding(12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(label, fontWeight = FontWeight.Medium)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (activeLabel != null) {
            item {
                Text(
                    "For \"$activeLabel\":",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (results.isEmpty()) {
                item {
                    Text(
                        "Nothing in the catalog matches that yet — the library " +
                            "is still small, so picks are thin for now.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(results.chunked(2)) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { m ->
                            PosterItem(m, Modifier.weight(1f)) {
                                nav.navigate("detail/${m.id}")
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // The mood input + send button.
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Mood, occasion, or a title…") },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(28.dp)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (input.isNotBlank()) {
                            typed = input.trim()
                            picked = null
                        }
                    },
                    modifier = Modifier.size(52.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Filled.Send,
                        "Send",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
