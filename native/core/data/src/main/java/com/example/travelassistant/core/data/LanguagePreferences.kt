package com.example.travelassistant.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.example.travelassistant.core.domain.*
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanguagePreferences @Inject constructor(private val store: DataStore<Preferences>) : LanguageRepository {
    private object Keys {
        val language = stringPreferencesKey("language")
        val initialized = booleanPreferencesKey("locale_initialized")
        val platform = stringPreferencesKey("last_platform_language")
        val pending = stringPreferencesKey("pending_locale_apply")
    }
    private fun Preferences.toSettings() = LanguageSettings(
        AppLanguage.fromTag(this[Keys.language]), this[Keys.initialized] ?: false,
        this[Keys.platform].orEmpty(), this[Keys.pending].orEmpty()
    )
    override val settings = store.data.map { it.toSettings() }
    override suspend fun requestLanguage(language: AppLanguage) {
        store.edit { p ->
            p[Keys.language] = language.tag
            p[Keys.pending] = language.tag
        }
    }
    override suspend fun resolveStartup(platformTag: String?, supportsPlatform: Boolean): AppLanguage {
        var desired = AppLanguage.ENGLISH
        // Resolve and commit from one snapshot; do not overwrite a newer selection
        // between a separate read and write during recovery.
        store.edit { p ->
            desired = LanguagePolicy.startup(p.toSettings(), platformTag, supportsPlatform)
            p[Keys.language] = desired.tag
            p[Keys.pending] = desired.tag
        }
        return desired
    }
    override suspend fun markApplied(language: AppLanguage, platformTag: String?) {
        store.edit { p ->
            // Do not acknowledge an older apply after a newer user selection.
            if (p[Keys.language] == language.tag) {
                p[Keys.initialized] = true
                p[Keys.platform] = platformTag.orEmpty()
                p.remove(Keys.pending)
            }
        }
    }
}
