package com.example.ai_travel_assistant.ui.format

import android.content.res.Resources
import com.example.ai_travel_assistant.R

private val cityZones = mapOf(
    R.string.city_hanoi to "Asia/Ho_Chi_Minh",
    R.string.city_hcmc to "Asia/Ho_Chi_Minh",
    R.string.city_danang to "Asia/Ho_Chi_Minh",
    R.string.city_dalat to "Asia/Ho_Chi_Minh",
    R.string.city_nhatrang to "Asia/Ho_Chi_Minh",
    R.string.city_bangkok to "Asia/Bangkok",
    R.string.city_singapore to "Asia/Singapore",
    R.string.city_tokyo to "Asia/Tokyo",
    R.string.city_seoul to "Asia/Seoul",
    R.string.city_ahmedabad to "Asia/Kolkata",
    R.string.city_goa to "Asia/Kolkata",
)

internal fun Resources.tripCityTimeZone(city: String): String? =
    cityZones.entries.firstOrNull { getString(it.key) == city }?.value
