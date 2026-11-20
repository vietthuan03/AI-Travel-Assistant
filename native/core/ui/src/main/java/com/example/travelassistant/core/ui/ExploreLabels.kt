package com.example.travelassistant.core.ui

import androidx.fragment.app.Fragment
import com.example.travelassistant.core.domain.ExploreDestination

fun Fragment.destinationTitle(destination: ExploreDestination): String = getString(when (destination.placeId) {
    "danang-vn" -> R.string.explore_danang
    "hanoi-vn" -> R.string.explore_hanoi
    "hochiminh-vn" -> R.string.explore_hochiminh
    else -> R.string.destination_label
})
