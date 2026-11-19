package com.example.travelassistant.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.travelassistant.core.domain.AppLanguage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LanguagePreferencesTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun acknowledgmentOfAnOldApplyCannotClearANewerSelection() = runTest {
        val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        try {
            val file = folder.newFolder().resolve("settings.preferences_pb")
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            val repository = LanguagePreferences(store)
            repository.resolveStartup("en", true)
            repository.markApplied(AppLanguage.ENGLISH, "en")
            repository.requestLanguage(AppLanguage.VIETNAMESE)
            repository.markApplied(AppLanguage.ENGLISH, "en")
            val pending = repository.settings.first()
            assertEquals(AppLanguage.VIETNAMESE, pending.language)
            assertEquals("vi", pending.pendingTag)
            assertEquals("en", pending.lastPlatformTag)
            assertEquals(AppLanguage.VIETNAMESE, repository.resolveStartup("en", true))
            repository.markApplied(AppLanguage.VIETNAMESE, "vi")
            assertEquals("", repository.settings.first().pendingTag)
            // After acknowledgment, an explicit platform reset must win over old preferences.
            assertEquals(AppLanguage.ENGLISH, repository.resolveStartup("", true))
        } finally { scope.cancel() }
    }

    @Test fun storedVietnameseSurvivesClosingAndReopeningThePreferenceStore() = runTest {
        val file = folder.newFolder().resolve("settings.preferences_pb")
        val firstJob = SupervisorJob()
        val firstScope = CoroutineScope(firstJob + UnconfinedTestDispatcher(testScheduler))
        val first = LanguagePreferences(PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }))
        first.requestLanguage(AppLanguage.VIETNAMESE)
        firstJob.cancelAndJoin()
        val secondScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        try {
            val second = LanguagePreferences(PreferenceDataStoreFactory.create(scope = secondScope, produceFile = { file }))
            assertEquals(AppLanguage.VIETNAMESE, second.resolveStartup(null, false))
        } finally { secondScope.cancel() }
    }
}
