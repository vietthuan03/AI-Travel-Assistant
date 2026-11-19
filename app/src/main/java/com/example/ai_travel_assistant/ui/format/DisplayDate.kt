package com.example.ai_travel_assistant.ui.format

import com.example.ai_travel_assistant.domain.trip.formatUtc
import java.text.DateFormat
import java.util.Locale

internal fun Long.asDisplayDate(locale: Locale): String =
    DateFormat.getDateInstance(DateFormat.SHORT, locale).formatUtc(this)
