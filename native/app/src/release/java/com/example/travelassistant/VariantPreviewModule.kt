package com.example.travelassistant

import com.example.travelassistant.core.domain.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.math.BigDecimal
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object VariantPreviewModule {
    @Provides @Singleton fun previews(): UiPreviewCatalog = VariantPreviewCatalog()
}
private class VariantPreviewCatalog : UiPreviewCatalog {
    override val scenarios = emptyList<PreviewScenario>()
    override fun plan(): PlanPresentation? = null
    override fun draft() = PlannerDraft()
}
