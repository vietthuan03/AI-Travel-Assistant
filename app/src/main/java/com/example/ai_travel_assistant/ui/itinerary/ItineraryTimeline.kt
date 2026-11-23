package com.example.ai_travel_assistant.ui.itinerary

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.ai_travel_assistant.R
import com.example.ai_travel_assistant.domain.trip.ItineraryDay
import com.example.ai_travel_assistant.domain.trip.ItineraryStop

@Composable
internal fun ItineraryTimeline(days: List<ItineraryDay>, destination: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        days.forEachIndexed { index, day ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.itinerary_day, index + 1),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (day.title.isNotBlank()) {
                    Text(day.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                }
                if (day.date.isNotBlank()) {
                    Text(day.date, style = MaterialTheme.typography.labelMedium)
                }
                if (day.stops.isEmpty()) {
                    Text(stringResource(R.string.plan_no_remaining_stops), style = MaterialTheme.typography.bodyMedium)
                }
                day.stops.forEachIndexed { stopIndex, stop ->
                    TimelineStop(stop, destination, stopIndex < day.stops.lastIndex)
                }
            }
        }
    }
}

@Composable
private fun TimelineStop(stop: ItineraryStop, destination: String, hasNext: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().drawBehind {
            if (hasNext) {
                val iconCenter = 18.dp.toPx()
                val x = if (layoutDirection == LayoutDirection.Ltr) iconCenter else size.width - iconCenter
                drawLine(colors.outlineVariant, Offset(x, iconCenter), Offset(x, size.height + 8.dp.toPx()), 2.dp.toPx())
            }
        },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(shape = CircleShape, color = colors.primaryContainer) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                val symbol = when (stop.category.lowercase()) {
                    "transport" -> "🚗"
                    "food" -> "🍜"
                    "sightseeing" -> "🏞️"
                    "rest" -> "🛏️"
                    else -> "📍"
                }
                Text(symbol, modifier = Modifier.clearAndSetSemantics {})
            }
        }
        Surface(
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.medium,
            color = colors.surfaceContainerLow,
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                val timing = listOf(stop.time, stop.duration).filter(String::isNotBlank).joinToString(" · ")
                if (timing.isNotEmpty()) Text(timing, color = colors.primary, style = MaterialTheme.typography.labelLarge)
                Text(stop.title, style = MaterialTheme.typography.titleMedium)
                listOf(stop.from to R.string.itinerary_from, stop.to to R.string.itinerary_to).forEach { (point, label) ->
                    if (point.isNotBlank()) Text(stringResource(label, point), style = MaterialTheme.typography.bodyMedium)
                }
                val location = listOf(stop.place, stop.area).filter(String::isNotBlank).distinct().joinToString(" · ")
                if (location.isNotBlank() && location != stop.title) {
                    Text(location, style = MaterialTheme.typography.bodyMedium)
                }
                if (stop.address.isNotBlank()) {
                    Text(stringResource(R.string.itinerary_address, stop.address), style = MaterialTheme.typography.bodyMedium)
                }
                if (stop.notes.isNotBlank()) Text(stop.notes, style = MaterialTheme.typography.bodyMedium)
                val mapPlace = stop.place.ifBlank { stop.address }
                if (mapPlace.isNotBlank()) {
                    val mapQuery = listOf(mapPlace, stop.area.ifBlank { destination })
                        .filter(String::isNotBlank).distinct().joinToString(", ")
                    val uriHandler = LocalUriHandler.current
                    TextButton(onClick = {
                        val url = Uri.parse("https://www.google.com/maps/search/").buildUpon()
                            .appendQueryParameter("api", "1").appendQueryParameter("query", mapQuery).build()
                        try {
                            uriHandler.openUri(url.toString())
                        } catch (_: IllegalArgumentException) {
                            // The device has no handler for web links.
                        }
                    }) {
                        Text(stringResource(R.string.itinerary_open_map))
                    }
                }
            }
        }
    }
}
