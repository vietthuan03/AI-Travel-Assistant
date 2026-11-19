package com.example.travelassistant.core.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.example.travelassistant.core.domain.*
import com.example.travelassistant.core.domain.PlanStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object DataProviders {
    @Provides @Singleton fun preferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { context.preferencesDataStoreFile("settings") }
        )
    @Provides @Singleton fun database(@ApplicationContext context: Context): LocalDatabase =
        Room.databaseBuilder(context, LocalDatabase::class.java, "travel.db").addMigrations(LocalDatabase.MIGRATION_1_2,LocalDatabase.MIGRATION_2_3,LocalDatabase.MIGRATION_3_4).build()
    @Provides fun workspace(db: LocalDatabase): WorkspaceDao = db.workspace()
    @Provides fun trips(db: LocalDatabase): TripDao = db.trips()
}
@Module @InstallIn(SingletonComponent::class)
abstract class RepositoryBindings {
    @Binds @Singleton abstract fun generation(impl: LocalGenerationRepository): com.example.travelassistant.core.domain.GenerationRepository
    @Binds @Singleton abstract fun plans(impl: RoomPlanStore): com.example.travelassistant.core.domain.PlanStore
    @Binds @Singleton abstract fun workspace(impl: LocalWorkspaceRepository): WorkspaceRepository
    @Binds @Singleton abstract fun display(impl: DisplayPreferencesStore): DisplayPreferencesRepository
    @Binds @Singleton abstract fun language(impl: LanguagePreferences): LanguageRepository
    @Binds @Singleton abstract fun trips(impl: LocalTripRepository): TripRepository
}
