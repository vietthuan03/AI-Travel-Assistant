package com.example.ai_travel_assistant.feature.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ai_travel_assistant.R
import com.example.ai_travel_assistant.ui.format.asDisplayDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SuggestedTripField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    suggestions: List<String>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    allowClear: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val menuOpen = expanded && enabled && suggestions.isNotEmpty()
    ExposedDropdownMenuBox(
        expanded = menuOpen,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
            enabled = enabled,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            colors = tripFieldColors(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen) },
        )
        ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { expanded = false }) {
            if (allowClear && value.isNotBlank()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.trip_clear_selection)) },
                    onClick = {
                        onValueChange("")
                        expanded = false
                    },
                )
            }
            suggestions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun tripFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface,
)

private enum class TripDateField { Departure, Return }

@Composable
internal fun TripDates(
    today: Long,
    departure: Long,
    returnDate: Long?,
    onDepartureChange: (Long) -> Unit,
    onReturnChange: (Long?) -> Unit,
    invalidDates: Boolean,
    enabled: Boolean,
) {
    var dateField by rememberSaveable { mutableStateOf<TripDateField?>(null) }
    val focus = LocalFocusManager.current
    val departureLabel = stringResource(R.string.travel_date)
    val returnLabel = stringResource(R.string.return_date)

    val dateButtons: @Composable (Modifier) -> Unit = { modifier ->
        TripDateButton(departureLabel, departure, enabled, modifier = modifier) {
            focus.clearFocus()
            dateField = TripDateField.Departure
        }
        TripDateButton(returnLabel, returnDate, enabled, invalidDates, modifier) {
            focus.clearFocus()
            dateField = TripDateField.Return
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints {
            if (maxWidth < (280 * LocalDensity.current.fontScale).dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    dateButtons(Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    dateButtons(Modifier.weight(1f))
                }
            }
        }
        if (invalidDates) {
            Text(
                text = stringResource(R.string.return_date_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }

    val selection = dateField
    if (enabled && selection == TripDateField.Departure) {
        DepartureDateDialog(departureLabel, departure, today, { dateField = null }) {
            onDepartureChange(it)
            dateField = null
        }
    } else if (enabled && selection == TripDateField.Return) {
        TripDateDialog(
            label = returnLabel,
            selected = returnDate,
            earliest = departure,
            onDismiss = { dateField = null },
            onDateChange = { date ->
                onReturnChange(date)
                dateField = null
            },
        )
    }
}

@Composable
private fun TripDateButton(
    label: String,
    date: Long?,
    enabled: Boolean,
    isError: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val value = remember(date, locale) { date?.asDisplayDate(locale) }
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { role = Role.Button },
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isError) colors.errorContainer else colors.surface,
            contentColor = if (isError) colors.onErrorContainer else colors.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_trip_calendar),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (enabled && !isError) colors.primary else LocalContentColor.current,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(
                    text = value ?: stringResource(R.string.choose_date),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun TripDateDialog(
    label: String,
    selected: Long?,
    earliest: Long?,
    onDismiss: () -> Unit,
    onDateChange: (Long?) -> Unit,
) {
    val selectableDates = remember(earliest) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                earliest == null || utcTimeMillis >= earliest
        }
    }
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = selected?.takeIf(selectableDates::isSelectableDate),
        initialDisplayedMonthMillis = selected?.takeIf(selectableDates::isSelectableDate) ?: earliest,
        initialDisplayMode = DisplayMode.Picker,
        selectableDates = selectableDates,
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    picker.selectedDateMillis?.takeIf(selectableDates::isSelectableDate)
                        ?.let(onDateChange)
                },
                enabled = picker.selectedDateMillis?.let(selectableDates::isSelectableDate) == true,
            ) {
                Text(stringResource(R.string.confirm_date))
            }
        },
        dismissButton = {
            Row {
                if (selected != null) {
                    TextButton(onClick = { onDateChange(null) }) {
                        Text(stringResource(R.string.clear_date))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        },
    ) {
        DatePicker(
            state = picker,
            showModeToggle = false,
            title = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                )
            },
        )
    }
}
