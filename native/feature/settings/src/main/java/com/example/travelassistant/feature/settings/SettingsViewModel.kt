package com.example.travelassistant.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelassistant.core.domain.AppLanguage
import com.example.travelassistant.core.domain.LanguageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SettingsError { READ, SAVE }

data class SettingsUiState(
    val language: AppLanguage? = null,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val error: SettingsError? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(private val languages: LanguageRepository) : ViewModel() {
    private val mutable = MutableStateFlow(SettingsUiState())
    val state = mutable.asStateFlow()
    private var readJob: Job? = null
    private var retrySelection: AppLanguage? = null

    init { observePreferences() }

    private fun observePreferences() {
        readJob?.cancel()
        mutable.update { it.copy(error = null) }
        readJob = viewModelScope.launch {
            languages.settings.catch {
                mutable.update { it.copy(loaded = false, error = SettingsError.READ) }
            }.collect { prefs ->
                mutable.update { it.copy(language = prefs.language, loaded = true) }
            }
        }
    }

    fun select(language: AppLanguage) {
        if (!mutable.value.loaded || mutable.value.saving || mutable.value.language == language) return
        // Block another click immediately, before scheduling the write coroutine.
        retrySelection = language
        mutable.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                languages.requestLanguage(language)
                retrySelection = null
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                mutable.update { it.copy(error = SettingsError.SAVE) }
            } finally {
                mutable.update { it.copy(saving = false) }
            }
        }
    }

    fun retry() {
        when (mutable.value.error) {
            SettingsError.READ -> observePreferences()
            SettingsError.SAVE -> retrySelection?.let(::select)
            null -> Unit
        }
    }
}
