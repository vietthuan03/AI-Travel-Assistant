package com.example.ai_travel_assistant.feature.planner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ai_travel_assistant.R
import java.text.DateFormat
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

private val utc = TimeZone.getTimeZone("UTC")
private const val dayMillis = 86_400_000L

internal fun todayForPicker(local: Calendar = GregorianCalendar()): Long =
    GregorianCalendar(utc).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis

@Composable
internal fun rememberTodayForPicker(): Long {
    val context = LocalContext.current
    var today by remember { mutableLongStateOf(todayForPicker()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { today = todayForPicker() }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        today = todayForPicker()
        onDispose { context.unregisterReceiver(receiver) }
    }
    return today
}

private fun monthStart(date: Long, offset: Int = 0): Long = GregorianCalendar(utc).apply {
    timeInMillis = date
    set(Calendar.DAY_OF_MONTH, 1)
    add(Calendar.MONTH, offset)
}.timeInMillis

@Composable
internal fun DepartureDateDialog(
    label: String, departure: Long, today: Long, dismiss: () -> Unit, confirm: (Long) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    var chosen by rememberSaveable { mutableLongStateOf(departure) }
    var browsing by rememberSaveable { mutableLongStateOf(monthStart(departure)) }
    val month = maxOf(browsing, monthStart(today))
    val calendar = GregorianCalendar(utc, locale).apply { timeInMillis = month }
    val firstDay = calendar.firstDayOfWeek
    val offset = (calendar.get(Calendar.DAY_OF_WEEK) - firstDay + 7) % 7
    val days = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val weekdays = remember(locale) { DateFormatSymbols(locale).shortWeekdays }
    val monthFormat = remember(locale) {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMM")
        SimpleDateFormat(pattern, locale).apply { timeZone = utc }
    }
    val fullDate = remember(locale) { DateFormat.getDateInstance(DateFormat.FULL, locale).apply { timeZone = utc } }
    AlertDialog(onDismissRequest = dismiss, title = { Text(label) },
        confirmButton = {
            TextButton(onClick = { confirm(chosen) }, enabled = chosen >= today) {
                Text(stringResource(R.string.confirm_date))
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(android.R.string.cancel)) } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val previous = stringResource(R.string.previous_month)
                    val next = stringResource(R.string.next_month)
                    IconButton(onClick = { browsing = monthStart(month, -1) }, enabled = month > monthStart(today),
                        modifier = Modifier.semantics { contentDescription = previous }) { Text("‹") }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(monthFormat.format(month), style = MaterialTheme.typography.titleSmall)
                    }
                    IconButton(onClick = { browsing = monthStart(month, 1) },
                        enabled = calendar.get(Calendar.YEAR) < 2100 || calendar.get(Calendar.MONTH) < Calendar.DECEMBER,
                        modifier = Modifier.semantics { contentDescription = next }) { Text("›") }
                }
                Row {
                    repeat(7) { index ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(weekdays[(firstDay - 1 + index) % 7 + 1], style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                val firstVisibleDay = ((maxOf(today, month) - month) / dayMillis).toInt()
                val firstWeek = (offset + firstVisibleDay) / 7
                for (week in firstWeek until (offset + days + 6) / 7) {
                    Row {
                        repeat(7) { column ->
                            val day = week * 7 + column - offset + 1
                            val date = month + (day - 1) * dayMillis
                            val cell = Modifier.weight(1f).heightIn(min = 48.dp)
                            if (day !in 1..days || date < today) {
                                Spacer(cell)
                            } else {
                                val selected = date == chosen
                                Surface(
                                    modifier = cell.selectable(selected, role = Role.RadioButton) { chosen = date }
                                        .semantics { contentDescription = fullDate.format(date) },
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                ) {
                                    Box(contentAlignment = Alignment.Center) { Text(day.toString()) }
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}
