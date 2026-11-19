package com.example.travelassistant.feature.settings

import androidx.lifecycle.ViewModelStore
import com.example.travelassistant.core.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { models.clear(); Dispatchers.resetMain() }

    private fun model(repository: FakeLanguages) = SettingsViewModel(repository).also { models.put("settings", it) }

    @Test fun failedWriteRetainsVietnameseAndExplicitRetrySavesEnglish() = runTest(dispatcher) {
        val repository = FakeLanguages()
        val model = model(repository)
        runCurrent()
        repository.failWrite = true
        model.select(AppLanguage.ENGLISH)
        runCurrent()
        assertEquals(AppLanguage.VIETNAMESE, model.state.value.language)
        assertEquals(SettingsError.SAVE, model.state.value.error)
        assertFalse(model.state.value.saving)
        repository.failWrite = false
        model.retry()
        runCurrent()
        assertEquals(AppLanguage.ENGLISH, model.state.value.language)
        assertNull(model.state.value.error)
        assertEquals(2, repository.writes)
    }

    @Test fun readFailureDisablesSelectionUntilExplicitRetryRestoresStoredLanguage() = runTest(dispatcher) {
        val repository = FakeLanguages().apply { failRead = true }
        val model = model(repository)
        runCurrent()
        assertFalse(model.state.value.loaded)
        assertEquals(SettingsError.READ, model.state.value.error)
        model.select(AppLanguage.ENGLISH)
        runCurrent()
        assertEquals(0, repository.writes)
        repository.failRead = false
        model.retry()
        runCurrent()
        assertTrue(model.state.value.loaded)
        assertEquals(AppLanguage.VIETNAMESE, model.state.value.language)
        assertNull(model.state.value.error)
    }

    @Test fun repeatedClicksBeforeTheWriteRunsProduceOnePreferenceWrite() = runTest(dispatcher) {
        val repository = FakeLanguages()
        val model = model(repository)
        // No selection may overwrite the unknown stored choice before initial read.
        model.select(AppLanguage.ENGLISH)
        assertEquals(0, repository.writes)
        runCurrent()
        model.select(AppLanguage.ENGLISH)
        model.select(AppLanguage.ENGLISH)
        runCurrent()
        assertEquals(1, repository.writes)
    }

    private class FakeLanguages : LanguageRepository {
        val current = MutableStateFlow(LanguageSettings(language = AppLanguage.VIETNAMESE))
        var failRead = false
        var failWrite = false
        var writes = 0
        override val settings = flow {
            if (failRead) throw IOException("read failure")
            emitAll(current)
        }
        override suspend fun requestLanguage(language: AppLanguage) {
            writes++
            if (failWrite) throw IOException("write failure")
            current.value = current.value.copy(language = language)
        }
        override suspend fun resolveStartup(platformTag: String?, supportsPlatform: Boolean) = current.value.language
        override suspend fun markApplied(language: AppLanguage, platformTag: String?) = Unit
    }
}
