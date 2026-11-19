package com.example.travelassistant.core.data
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.travelassistant.core.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
@OptIn(ExperimentalCoroutinesApi::class)
class DisplayPreferencesStoreTest {
    @get:Rule val folder=TemporaryFolder()
    @Test fun displayChangesDoNotOverwriteLanguageOrOtherPreferenceKeys() = runTest {
        val scope=CoroutineScope(SupervisorJob()+UnconfinedTestDispatcher(testScheduler))
        try {
            val file=folder.newFolder().resolve("settings.preferences_pb")
            val store=PreferenceDataStoreFactory.create(scope=scope,produceFile={file})
            val languages=LanguagePreferences(store);val display=DisplayPreferencesStore(store)
            languages.requestLanguage(AppLanguage.VIETNAMESE)
            display.update("EUR","fahrenheit")
            assertEquals(AppLanguage.VIETNAMESE,languages.settings.first().language)
            assertEquals("vi",languages.settings.first().pendingTag)
            languages.markApplied(AppLanguage.VIETNAMESE,"vi")
            assertEquals(DisplayPreferences("EUR","fahrenheit"),display.preferences.first())
        } finally { scope.cancel() }
    }
}
