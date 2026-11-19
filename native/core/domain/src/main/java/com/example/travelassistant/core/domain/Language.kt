package com.example.travelassistant.core.domain

import kotlinx.coroutines.flow.Flow

enum class AppLanguage(val tag: String) {
    ENGLISH("en"), VIETNAMESE("vi");
    companion object {
        fun fromTag(tag: String?): AppLanguage = if (tag?.substringBefore('-') == "vi") VIETNAMESE else ENGLISH
    }
}
data class LanguageSettings(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val initialized: Boolean = false,
    val lastPlatformTag: String = "",
    val pendingTag: String = ""
)
object LanguagePolicy {
    fun startup(saved: LanguageSettings, platformTag: String?, supportsPlatform: Boolean): AppLanguage = when {
        saved.pendingTag.isNotEmpty() -> AppLanguage.fromTag(saved.pendingTag)
        !supportsPlatform -> saved.language
        !saved.initialized && !platformTag.isNullOrEmpty() -> AppLanguage.fromTag(platformTag)
        saved.initialized && platformTag.orEmpty() != saved.lastPlatformTag -> AppLanguage.fromTag(platformTag)
        else -> saved.language
    }
}
interface LanguageRepository {
    val settings: Flow<LanguageSettings>
    suspend fun requestLanguage(language: AppLanguage)
    suspend fun resolveStartup(platformTag: String?, supportsPlatform: Boolean): AppLanguage
    suspend fun markApplied(language: AppLanguage, platformTag: String?)
}
data class SavedTripSummary(val id: String, val title: String)
interface TripRepository { val savedTrips: Flow<List<SavedTripSummary>> }
