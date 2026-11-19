package com.example.travelassistant.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.example.travelassistant.core.domain.*
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DisplayPreferencesStore @Inject constructor(private val store: DataStore<Preferences>) : DisplayPreferencesRepository {
    private val currency = stringPreferencesKey("display_currency")
    private val units = stringPreferencesKey("temperature_units")
    override val preferences = store.data.map { p -> DisplayPreferences(
        p[currency]?.takeIf { it in TravelCatalog.currencies } ?: "USD",
        p[units]?.takeIf { it in setOf("celsius", "fahrenheit") } ?: "celsius"
    ) }
    override suspend fun update(currency: String, units: String) {
        require(currency in TravelCatalog.currencies && units in setOf("celsius", "fahrenheit"))
        store.edit { p -> p[this.currency] = currency; p[this.units] = units }
    }
}
