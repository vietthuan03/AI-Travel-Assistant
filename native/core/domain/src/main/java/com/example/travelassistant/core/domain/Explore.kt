package com.example.travelassistant.core.domain

import kotlinx.coroutines.flow.Flow

enum class DestinationTheme { CULTURE, COAST, CITY }

data class ExploreDestination(
    val placeId: String,
    val theme: DestinationTheme,
    val airport: String,
    val tourismUrl: String,
)

object ExploreCatalog {
    val destinations = listOf(
        ExploreDestination("danang-vn", DestinationTheme.COAST, "DAD", "https://vietnam.travel/place-to-go/south-central-coast-central-highlands/da-nang/"),
        ExploreDestination("hanoi-vn", DestinationTheme.CULTURE, "HAN", "https://vietnam.travel/place-to-go/red-river-delta/ha-noi/"),
        ExploreDestination("hochiminh-vn", DestinationTheme.CITY, "SGN", "https://vietnam.travel/place-to-go/southeastern-vietnam/ho-chi-minh-city/"),
    )

    fun destination(id: String?) = destinations.firstOrNull { it.placeId == id }
}

interface SavedDestinationsRepository {
    val saved: Flow<Set<String>>
    suspend fun setSaved(destination: ExploreDestination, saved: Boolean)
    suspend fun clear()
}
