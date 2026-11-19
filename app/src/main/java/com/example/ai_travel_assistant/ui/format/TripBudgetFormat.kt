package com.example.ai_travel_assistant.ui.format

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

private val groupedBudget = Regex("""[0-9]{1,3}(\.[0-9]{3})+""")

internal fun normalizeTripBudget(input: String): String? {
    if (!input.all { it in '0'..'9' } && !groupedBudget.matches(input)) return null
    return input.replace(".", "").takeIf { it.length <= 18 }
}

internal fun formatTripBudget(digits: String): String =
    digits.reversed().chunked(3).joinToString(".").reversed()

internal object TripBudgetVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val formatted = formatTripBudget(text.text)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset == 0) return 0
                val firstGroupSize = (text.length - 1) % 3 + 1
                val separatorsBefore = (offset + 2 - firstGroupSize).coerceAtLeast(0) / 3
                return offset + separatorsBefore
            }

            override fun transformedToOriginal(offset: Int): Int =
                formatted.take(offset).count { it != '.' }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}
