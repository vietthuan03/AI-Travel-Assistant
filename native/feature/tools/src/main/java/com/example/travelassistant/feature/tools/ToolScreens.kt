package com.example.travelassistant.feature.tools

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.*
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import com.example.travelassistant.core.domain.*
import com.example.travelassistant.core.ui.*
import com.example.travelassistant.core.ui.R as UiR
import com.example.travelassistant.feature.tools.databinding.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class CurrencyFragment : Fragment() {
    @Inject lateinit var catalog: UiPreviewCatalog
    private val model: ToolViewModel by viewModels()
    private var binding: FragmentCurrencyBinding?=null
    private var rendering=false
    private var inputReady=false
    override fun onCreateView(inflater: LayoutInflater,container: ViewGroup?,state: Bundle?): View = FragmentCurrencyBinding.inflate(inflater,container,false).also { binding=it }.root
    override fun onViewCreated(view: View,state: Bundle?) {
        val b=binding!!
        b.amount.numericInput()
        b.fromCurrency.options(TravelCatalog.currencies);b.toCurrency.options(TravelCatalog.currencies)
        b.fromCurrency.keyListener=null;b.toCurrency.keyListener=null
        b.amount.doAfterTextChanged { if(inputReady && !rendering) model.input("amount",it.toString()) }
        b.fromCurrency.setOnItemClickListener { parent,_,i,_ -> model.input("from",parent.getItemAtPosition(i).toString()) }
        b.toCurrency.setOnItemClickListener { parent,_,i,_ -> model.input("to",parent.getItemAtPosition(i).toString()) }
        b.swap.setOnClickListener { model.swap() }
        fun convert() {
            val d=model.inputs.value
            model.setStatus(if(BriefValidator.decimal(d.amount)?.signum()?.let { it>0 }!=true) "invalid" else if(d.from==d.to) "identity" else "unavailable")
        }
        b.convert.setOnClickListener { convert() };b.rateRetry.setOnClickListener { convert() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.inputs.collect { d ->
                    rendering=true
                    if(b.amount.text.toString()!=d.amount) b.amount.setText(d.amount)
                    b.fromCurrency.choose(d.from);b.toCurrency.choose(d.to)
                    b.amountLayout.error=if(d.status=="invalid") getString(UiR.string.error_amount) else null
                    val sample=preview(catalog)
                    b.previewBanner.isVisible=sample!=null
                    b.currencyStatus.text=when {
                        d.status=="identity" -> getString(UiR.string.same_currency,BriefValidator.decimal(d.amount)?.toPlainString(),d.from)
                        sample!=null && sample.state!="normal" && !d.touched -> previewTitle(sample)
                        else -> getString(UiR.string.rate_unavailable)
                    }
                    b.currencyStatus.isVisible=true
                    b.rateSource.isVisible=d.status!="identity"
                    rendering=false
                }
            }
        }
    }
    override fun onViewStateRestored(state: Bundle?) {
        super.onViewStateRestored(state)
        inputReady=true
    }
    override fun onDestroyView() { inputReady=false;binding=null;super.onDestroyView() }
}

@AndroidEntryPoint
class WorldTimeFragment : Fragment() {
    @Inject lateinit var catalog: UiPreviewCatalog
    private val model: WorldTimeViewModel by viewModels()
    private var binding: FragmentWorldTimeBinding?=null
    private val zones=TravelCatalog.places.map { it.zone }.distinct()+"UTC"
    override fun onCreateView(inflater: LayoutInflater,container: ViewGroup?,state: Bundle?): View = FragmentWorldTimeBinding.inflate(inflater,container,false).also { binding=it }.root
    override fun onViewCreated(view: View,state: Bundle?) {
        val b=binding!!
        b.previewBanner.isVisible=preview(catalog)!=null
        b.timeDestination.options(zones);b.timeDestination.keyListener=null
        b.timeDestination.setOnItemClickListener { parent,_,i,_ -> model.select(parent.getItemAtPosition(i).toString()) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(model.now,model.zone) { instant,zone -> instant to zone }.collect { (instant,zone) ->
                    val target=runCatching { ZoneId.of(zone) }.getOrDefault(ZoneOffset.UTC)
                    val local=ZoneId.systemDefault()
                    val formatter=DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(resources.configuration.locales[0])
                    b.localClock.text=formatter.withZone(local).format(instant)+"\n"+local.id
                    b.destinationClock.text=formatter.withZone(target).format(instant)+"\n"+target.id
                    b.timeDestination.choose(target.id)
                    val seconds=target.rules.getOffset(instant).totalSeconds-local.rules.getOffset(instant).totalSeconds
                    val difference=String.format(Locale.ROOT,"%s%02d:%02d",if(seconds>=0) "+" else "−",abs(seconds)/3600,abs(seconds)%3600/60)
                    b.difference.text=getString(UiR.string.zone_difference,difference)
                }
            }
        }
    }
    override fun onDestroyView() { binding=null;super.onDestroyView() }
}

@AndroidEntryPoint
class WeatherFragment : Fragment() {
    @Inject lateinit var catalog: UiPreviewCatalog
    private val model: ToolViewModel by viewModels()
    private var binding: FragmentWeatherBinding?=null
    private var rendering=false
    private var inputReady=false
    override fun onCreateView(inflater: LayoutInflater,container: ViewGroup?,state: Bundle?): View = FragmentWeatherBinding.inflate(inflater,container,false).also { binding=it }.root
    override fun onViewCreated(view: View,state: Bundle?) {
        val b=binding!!
        val places=TravelCatalog.places.map { placeLabel(it) }
        b.weatherCity.options(places)
        b.weatherCity.doAfterTextChanged { if(inputReady && !rendering) model.input("query",it.toString()) }
        b.weatherCity.setOnItemClickListener { parent,_,i,_ ->
            val label=parent.getItemAtPosition(i).toString()
            val place=TravelCatalog.places.first { placeLabel(it)==label }
            model.selectCity(place.id,label)
        }
        val units=listOf(getString(UiR.string.celsius),getString(UiR.string.fahrenheit))
        b.weatherUnits.options(units);b.weatherUnits.keyListener=null
        b.weatherUnits.setOnItemClickListener { parent,_,i,_ -> model.saveUnits(if(parent.getItemAtPosition(i).toString()==units[0]) "celsius" else "fahrenheit",preview(catalog)!=null) }
        fun search() { model.setStatus(if(TravelCatalog.place(model.inputs.value.city)==null) "invalid" else "unavailable") }
        b.searchWeather.setOnClickListener { search() }
        b.weatherRetry.setOnClickListener { if(model.preferenceFailed.value) model.retryPreferences() else search() }
        b.useLocation.setOnClickListener { model.setStatus("manual") }
        b.permissionSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+requireContext().packageName)))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(model.inputs,model.preferenceFailed,model.preferenceSaving) { d,failed,saving -> Triple(d,failed,saving) }.collect { (d,failed,saving) ->
                    rendering=true
                    val selected=TravelCatalog.place(d.city)
                    b.weatherCity.choose(selected?.let { placeLabel(it) } ?: d.query)
                    b.weatherSelectedPlace.isVisible=selected!=null
                    b.weatherSelectedPlace.text=selected?.let { placeLabel(it) }.orEmpty()
                    b.weatherUnits.choose(units[if(d.units=="celsius") 0 else 1]);b.weatherUnits.isEnabled=!saving
                    b.weatherCityLayout.error=if(d.status=="invalid") getString(UiR.string.error_destination) else null
                    val sample=preview(catalog)
                    b.previewBanner.isVisible=sample!=null
                    b.weatherStatus.text=when {
                        failed -> getString(if(model.preferenceWriteFailed) UiR.string.display_preferences_error else UiR.string.display_preferences_read_error)
                        d.status=="manual" -> getString(UiR.string.manual_location)
                        sample!=null && !d.touched && d.status.isBlank() -> if(sample.state=="normal") getString(UiR.string.weather_unavailable) else previewTitle(sample)
                        else -> getString(UiR.string.weather_unavailable)
                    }
                    b.weatherProgress.isVisible=sample?.state=="loading" && !d.touched && d.status.isBlank()
                    b.permissionSettings.isVisible=sample?.state=="permanent" && !d.touched
                    rendering=false
                }
            }
        }
    }
    override fun onViewStateRestored(state: Bundle?) {
        super.onViewStateRestored(state)
        inputReady=true
    }
    override fun onDestroyView() { inputReady=false;binding=null;super.onDestroyView() }
}
