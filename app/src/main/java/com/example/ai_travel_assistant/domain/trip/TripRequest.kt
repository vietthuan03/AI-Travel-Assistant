package com.example.ai_travel_assistant.domain.trip

internal data class TripRequest(
    val origin: String,
    val destination: String,
    val departure: Long?,
    val returnDate: Long?,
    val purpose: String,
    val pace: String,
    val notes: String,
    val budgetAmount: Long? = null,
    val budgetCurrency: String = "VND",
    val originTimeZone: String? = null,
    val destinationTimeZone: String? = null,
) {
    val hasInvalidDates: Boolean
        get() = departure != null && returnDate != null && returnDate < departure

    val hasRequiredDetails: Boolean
        get() = origin.isNotBlank() && destination.isNotBlank() && purpose.isNotBlank() &&
            departure != null && returnDate != null && !hasInvalidDates &&
            budgetAmount?.let { it > 0 } == true
}
