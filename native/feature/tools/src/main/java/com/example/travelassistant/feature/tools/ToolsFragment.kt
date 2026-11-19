package com.example.travelassistant.feature.tools

import android.os.Bundle
import android.view.View
import com.example.travelassistant.core.domain.UiPreviewCatalog
import com.example.travelassistant.core.ui.*
import com.example.travelassistant.core.ui.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ToolsFragment : MvpListFragment() {
    @Inject lateinit var catalog: UiPreviewCatalog
    override fun onViewCreated(view: View,state: Bundle?) {
        super.onViewCreated(view,state)
        if(redirectUiEntry(R.id.tools_fragment,"T01",keepStart=true)) return
        val list=mutableListOf<UiSection>()
        if(preview(catalog)!=null) list+=sampleBanner()
        list+=UiSection("intro",getString(R.string.tools_title),getString(R.string.tools_description),artwork=TravelArtwork.Tools)
        list+=UiSection("currency",getString(R.string.currency_title),getString(R.string.currency_intro),primary=action(R.string.currency_title,viewId=R.id.currency_action,icon=R.drawable.ic_tool_currency) { navigateUi(R.id.tools_fragment,R.id.currency_fragment) })
        list+=UiSection("time",getString(R.string.time_title),getString(R.string.world_time_intro),primary=action(R.string.time_title,viewId=R.id.time_action,icon=R.drawable.ic_tool_time) { navigateUi(R.id.tools_fragment,R.id.time_fragment) })
        list+=UiSection("weather",getString(R.string.weather_title),getString(R.string.weather_unavailable),primary=action(R.string.weather_title,viewId=R.id.weather_action,icon=R.drawable.ic_tool_weather) { navigateUi(R.id.tools_fragment,R.id.weather_fragment) })
        rows(list)
    }
}
