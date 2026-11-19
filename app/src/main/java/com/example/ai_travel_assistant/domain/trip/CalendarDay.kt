package com.example.ai_travel_assistant.domain.trip

import java.text.DateFormat
import java.util.*

// Material date pickers represent the selected calendar day at midnight UTC.
internal fun DateFormat.formatUtc(millis: Long): String {
    timeZone = TimeZone.getTimeZone("UTC")
    return format(Date(millis))
}
