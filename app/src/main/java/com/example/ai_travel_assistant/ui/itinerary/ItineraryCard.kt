package com.example.ai_travel_assistant.ui.itinerary

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ai_travel_assistant.R
import com.example.ai_travel_assistant.domain.trip.Itinerary
import com.example.ai_travel_assistant.domain.trip.ItineraryDay
import com.example.ai_travel_assistant.domain.trip.ItineraryStop
import com.example.ai_travel_assistant.ui.theme.AiTravelAssistantTheme

@Composable
internal fun PlanStatusHeader(
    title: String,
    @DrawableRes artwork: Int,
    modifier: Modifier,
    body: String = "",
    loading: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(artwork),
            contentDescription = null,
            modifier = Modifier.width(72.dp).aspectRatio(1.47f).clip(MaterialTheme.shapes.small),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodyMedium)
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun ItineraryCard(
    itinerary: Itinerary,
    route: String,
    headerModifier: Modifier,
    details: String = "",
    destination: String = "",
) {
    val days = itinerary.days
    val output = itinerary.text
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            PlanStatusHeader(
                title = stringResource(R.string.result_plan),
                body = listOf(route, details).filter(String::isNotBlank).joinToString("\n"),
                artwork = R.drawable.travel_state_ready,
                modifier = headerModifier,
            )
        }
        if (days != null) {
            ItineraryTimeline(days, destination)
            return@OutlinedCard
        }
        val blocks = remember(output) { itineraryBlocks(output) }
        SelectionContainer {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                blocks.forEachIndexed { index, block ->
                    if (block.heading) {
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() },
                        )
                    } else {
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

private data class ItineraryBlock(
    val text: AnnotatedString,
    val heading: Boolean = false,
)

private val markdownHeading = Regex("""^#{1,6}[ \t]+(.+)$""")
private val markdownBullet = Regex("""^([ \t]*)[-*+][ \t]+""")
private val markdownBold = Regex("""\*\*([^*\n]+)\*\*""")

// Style the common Markdown emitted by the model while keeping other lines visible.
private fun itineraryBlocks(output: String): List<ItineraryBlock> = buildList {
    val paragraph = mutableListOf<String>()
    fun addParagraph() {
        if (paragraph.isEmpty()) return
        add(ItineraryBlock(styleBold(paragraph.joinToString("\n"))))
        paragraph.clear()
    }

    output.lineSequence().forEach { line ->
        val heading = markdownHeading.matchEntire(line.trim())
        when {
            heading != null -> {
                addParagraph()
                add(ItineraryBlock(styleBold(heading.groupValues[1]), heading = true))
            }
            line.isBlank() -> addParagraph()
            else -> paragraph += markdownBullet.replaceFirst(line, "\$1• ")
        }
    }
    addParagraph()
}

private fun styleBold(text: String) = buildAnnotatedString {
    var start = 0
    markdownBold.findAll(text).forEach { match ->
        append(text.substring(start, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
        start = match.range.last + 1
    }
    append(text.substring(start))
}

@Preview(name = "Itinerary · Vietnamese", widthDp = 360, locale = "vi", showBackground = true)
@Composable
private fun ItineraryPreview() {
    AiTravelAssistantTheme {
        ItineraryCard(
            itinerary = Itinerary("", listOf(ItineraryDay("Đến Đà Nẵng", listOf(
                ItineraryStop("10:30", "Đến khách sạn", "transport", "", "", "",
                    "Sân bay Đà Nẵng", "Khách sạn", "", "Gửi hành lý trước khi tham quan.")
            )))),
            route = "Hà Nội → Đà Nẵng",
            details = "18/12 – 20/12 · Nghỉ dưỡng · 3.000.000 VND",
            destination = "Đà Nẵng",
            headerModifier = Modifier,
        )
    }
}
