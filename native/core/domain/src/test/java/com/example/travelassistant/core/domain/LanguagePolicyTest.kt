package com.example.travelassistant.core.domain
import org.junit.Assert.assertEquals
import org.junit.Test
class LanguagePolicyTest {
    @Test fun defaultIsEnglish() { assertEquals(AppLanguage.ENGLISH,LanguagePolicy.startup(LanguageSettings(),null,false)) }
    @Test fun legacyChoiceRestores() { assertEquals(AppLanguage.VIETNAMESE,LanguagePolicy.startup(LanguageSettings(language=AppLanguage.VIETNAMESE),null,false)) }
    @Test fun unsupportedTagFallsBack() { assertEquals(AppLanguage.ENGLISH,AppLanguage.fromTag("fr-FR")) }
    @Test fun regionalVietnameseNormalizes() { assertEquals(AppLanguage.VIETNAMESE,AppLanguage.fromTag("vi-VN")) }
    @Test fun externalResetDoesNotResurrectVietnamese() { assertEquals(AppLanguage.ENGLISH,LanguagePolicy.startup(LanguageSettings(AppLanguage.VIETNAMESE,true,"vi"),"",true)) }
    @Test fun externalChoiceWinsOverStalePreference() { assertEquals(AppLanguage.VIETNAMESE,LanguagePolicy.startup(LanguageSettings(AppLanguage.ENGLISH,true,"en"),"vi",true)) }
    @Test fun interruptedApplyCanRecover() { assertEquals(AppLanguage.VIETNAMESE,LanguagePolicy.startup(LanguageSettings(AppLanguage.VIETNAMESE,true,"en","vi"),"en",true)) }
}
