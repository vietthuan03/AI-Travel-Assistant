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

@Composable
internal fun ExploreDestination(cities: List<String>, city: String, onSelect: (String) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val daNang = stringResource(R.string.city_danang)
    val hanoi = stringResource(R.string.city_hanoi)
    val photo = when (city) {
        "" -> R.drawable.explore_coast
        daNang -> R.drawable.explore_danang
        hanoi -> R.drawable.explore_hanoi
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (photo != null) {
            Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)) {
                Image(painterResource(photo), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                if (city.isEmpty()) {
                    Column(Modifier.fillMaxWidth().heightIn(min = 216.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .7f))))
                        .padding(16.dp), verticalArrangement = Arrangement.Bottom) {
                        Text(stringResource(R.string.tab_explore), color = Color.White,
                            style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.explore_choose_city))
                        }
                    }
                } else Spacer(Modifier.height(184.dp))
            }
        }
        if (city.isEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(daNang to R.drawable.explore_danang, hanoi to R.drawable.explore_hanoi)
                    .filter { it.first in cities }.forEach { (name, image) ->
                        Card(onClick = { onSelect(name) }, modifier = Modifier.weight(1f)) {
                            Box(Modifier.fillMaxWidth().heightIn(min = 144.dp), contentAlignment = Alignment.BottomStart) {
                                Image(painterResource(image), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                                Text(name.substringBefore(','), Modifier.fillMaxWidth()
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .82f))))
                                    .padding(12.dp), color = Color.White, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(city.substringBefore(','), Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = { choosing = true }) { Text(stringResource(R.string.explore_change_city)) }
            }
        }
    }
    if (choosing) AlertDialog(onDismissRequest = { choosing = false },
        title = { Text(stringResource(R.string.explore_destination)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                cities.forEach { option ->
                    TextButton(onClick = { onSelect(option); choosing = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(option, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.history_close)) } })
}
