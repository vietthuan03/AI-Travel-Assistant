package com.example.ai_travel_assistant.feature.explore

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ai_travel_assistant.R
import java.text.Normalizer
import java.util.Locale

private data class DestinationArt(val city: Int, val photo: Int, val description: Int? = null)

private val destinationArt = listOf(
    DestinationArt(R.string.city_danang, R.drawable.explore_danang, R.string.explore_danang_summary),
    DestinationArt(R.string.city_hanoi, R.drawable.explore_hanoi, R.string.explore_hanoi_summary),
    DestinationArt(R.string.city_hcmc, R.drawable.explore_hcmc, R.string.explore_hcmc_summary),
    DestinationArt(R.string.city_dalat, R.drawable.explore_dalat),
    DestinationArt(R.string.city_nhatrang, R.drawable.explore_nhatrang),
    DestinationArt(R.string.city_bangkok, R.drawable.explore_bangkok),
    DestinationArt(R.string.city_singapore, R.drawable.explore_singapore),
    DestinationArt(R.string.city_tokyo, R.drawable.explore_tokyo),
    DestinationArt(R.string.city_seoul, R.drawable.explore_seoul),
    DestinationArt(R.string.city_ahmedabad, R.drawable.explore_ahmedabad),
    DestinationArt(R.string.city_goa, R.drawable.explore_goa),
)
private val combiningMarks = Regex("\\p{M}+")

@Composable
internal fun ExploreDestination(
    cities: List<String>,
    city: String,
    choosing: Boolean,
    onChoose: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    actions: @Composable () -> Unit,
) {
    val photos = destinationArt.associateBy { stringResource(it.city) }
    val selected = photos[city]
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 700.dp
        if (city.isNotEmpty() && wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                DestinationCover(selected?.photo ?: R.drawable.explore_coast, Modifier.weight(1.5f), 300)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DestinationHeading(city, selected, onChoose)
                    actions()
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (city.isEmpty()) {
                    Text(stringResource(R.string.tab_explore), style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.semantics { heading() })
                    Button(onClick = onChoose, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.explore_choose_city))
                    }
                    actions()
                } else {
                    DestinationCover(selected?.photo ?: R.drawable.explore_coast, Modifier.fillMaxWidth(), 220)
                    DestinationHeading(city, selected, onChoose)
                    actions()
                }
            }
        }
    }
    if (city.isEmpty()) {
        val featured = photos.entries.take(3).filter { it.key in cities }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 700.dp) 3 else 1
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                featured.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (name, art) ->
                            Card(onClick = { onSelect(name) }, modifier = Modifier.weight(1f)) {
                                Box(Modifier.fillMaxWidth().heightIn(min = 172.dp), contentAlignment = Alignment.BottomStart) {
                                    Image(painterResource(art.photo), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                                    Text(
                                        name.substringBefore(','),
                                        Modifier.fillMaxWidth()
                                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f))))
                                            .padding(16.dp),
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (choosing) {
        var query by rememberSaveable { mutableStateOf("") }
        val filter = query.trim().forDestinationSearch()
        val matching = cities.filter { it.forDestinationSearch().contains(filter) }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.explore_destination)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.explore_choose_city)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (matching.isEmpty()) {
                        Text(stringResource(R.string.explore_no_destinations))
                        TextButton(onClick = { query = "" }) { Text(stringResource(R.string.explore_clear_search)) }
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        matching.forEach { option ->
                            TextButton(onClick = { onSelect(option) }, modifier = Modifier.fillMaxWidth()) {
                                photos[option]?.let {
                                    Image(painterResource(it.photo), null, Modifier.size(52.dp, 36.dp)
                                        .clip(MaterialTheme.shapes.small), contentScale = ContentScale.Crop)
                                    Spacer(Modifier.width(12.dp))
                                }
                                Text(option, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.history_close)) } },
        )
    }
}

@Composable
private fun DestinationHeading(city: String, art: DestinationArt?, onChoose: () -> Unit) {
    Text(city.substringBefore(','), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
    art?.description?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge) }
    TextButton(onClick = onChoose) { Text(stringResource(R.string.explore_change_city)) }
}

@Composable
private fun DestinationCover(photo: Int, modifier: Modifier, height: Int) {
    Image(painterResource(photo), null, modifier.height(height.dp).clip(MaterialTheme.shapes.extraLarge),
        contentScale = ContentScale.Crop)
}

private fun String.forDestinationSearch(): String =
    Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(combiningMarks, "").replace('đ', 'd')
