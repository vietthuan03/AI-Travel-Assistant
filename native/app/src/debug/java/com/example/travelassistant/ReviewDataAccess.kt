package com.example.travelassistant

import com.example.travelassistant.core.data.LocalDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** In-process debug verification only; not exported and absent from release. */
@EntryPoint @InstallIn(SingletonComponent::class)
interface ReviewDataAccess {
 fun database(): LocalDatabase
 fun preferences(): androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
}
