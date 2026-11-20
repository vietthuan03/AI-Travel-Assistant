package com.example.travelassistant.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.travelassistant.core.domain.ExploreDestination
import com.example.travelassistant.core.domain.SavedDestinationsRepository
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LocalSavedDestinations @Inject constructor(
    private val preferences: DataStore<Preferences>,
) : SavedDestinationsRepository {
    private val key = stringSetPreferencesKey("saved_explore_destinations")

    override val saved = preferences.data.map { it[key].orEmpty() }

    override suspend fun setSaved(destination: ExploreDestination, saved: Boolean) {
        preferences.edit { values ->
            val current = values[key].orEmpty()
            values[key] = if (saved) current + destination.placeId else current - destination.placeId
        }
    }

    override suspend fun clear() {
        preferences.edit { it.remove(key) }
    }
}
