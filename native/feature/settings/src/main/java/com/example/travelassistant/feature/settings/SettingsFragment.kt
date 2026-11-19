package com.example.travelassistant.feature.settings

import android.os.Bundle
import android.view.*
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import com.example.travelassistant.core.domain.*
import com.example.travelassistant.core.ui.*
import com.example.travelassistant.core.ui.R
import com.example.travelassistant.feature.settings.databinding.FragmentSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment() {
    @Inject lateinit var catalog: UiPreviewCatalog
    private var binding: FragmentSettingsBinding?=null
    private val model: SettingsViewModel by viewModels()
    private val display: DisplaySettingsViewModel by viewModels()
    private val workspace: WorkspaceViewModel by activityViewModels()
    override fun onCreateView(inflater: LayoutInflater,container: ViewGroup?,state: Bundle?): View =
        FragmentSettingsBinding.inflate(inflater,container,false).also { binding=it }.root
    override fun onViewCreated(view: View,state: Bundle?) {
        val b=binding!!
        val sample=preview(catalog)
        b.settingsPreview.isVisible=sample!=null
        if(sample?.state=="fallback") b.settingsPreview.text=getString(R.string.preview_banner)+"\n"+getString(R.string.fallback_example)
        if(model.state.value.error==SettingsError.READ) model.retry()
        if(display.state.value.error==SettingsError.READ) display.retry()
        b.languageEnglish.setOnClickListener { if(sample!=null) sampleAction() else model.select(AppLanguage.ENGLISH) }
        b.languageVietnamese.setOnClickListener { if(sample!=null) sampleAction() else model.select(AppLanguage.VIETNAMESE) }
        b.languageRetry.setOnClickListener {
            if(sample!=null) sampleAction() else if(model.state.value.error==SettingsError.READ) requireActivity().recreate() else model.retry()
        }
        val unitLabels=listOf(getString(R.string.celsius),getString(R.string.fahrenheit))
        b.displayCurrency.options(TravelCatalog.currencies);b.displayCurrency.keyListener=null
        b.temperatureUnits.options(unitLabels);b.temperatureUnits.keyListener=null
        b.displayCurrency.setOnItemClickListener { parent,_,i,_ ->
            if(sample!=null) sampleAction() else display.update(display.state.value.preferences.copy(currency=parent.getItemAtPosition(i).toString()))
        }
        b.temperatureUnits.setOnItemClickListener { parent,_,i,_ ->
            if(sample!=null) sampleAction() else display.update(display.state.value.preferences.copy(units=if(parent.getItemAtPosition(i).toString()==unitLabels[0]) "celsius" else "fahrenheit"))
        }
        b.preferencesRetry.setOnClickListener {
            if(sample!=null) sampleAction() else if(display.state.value.error==SettingsError.READ) requireActivity().recreate() else display.retry()
        }
        fun info(title: Int,body: Int) { MaterialAlertDialogBuilder(requireContext()).setTitle(title).setMessage(body).setPositiveButton(R.string.close,null).show() }
        b.privacy.setOnClickListener { info(R.string.privacy_title,R.string.privacy_body) }
        b.retention.setOnClickListener { info(R.string.retention_title,R.string.retention_body) }
        b.help.setOnClickListener { info(R.string.help_title,R.string.help_body) }
        b.legal.setOnClickListener { info(R.string.legal_title,R.string.legal_body) }
        b.previewGallery.isVisible=catalog.scenarios.isNotEmpty()
        b.previewGallery.setOnClickListener { (requireActivity() as? UiPreviewHost)?.openUiPreview() }
        b.removeData.setOnClickListener {
            if(sample!=null) { sampleAction();return@setOnClickListener }
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.remove_data_title).setMessage(R.string.remove_data_body)
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.remove_confirm) { _,_ -> removeData() }.show()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.state.collect { ui ->
                    b.languageEnglish.isChecked=ui.language==AppLanguage.ENGLISH
                    b.languageVietnamese.isChecked=ui.language==AppLanguage.VIETNAMESE
                    b.languageEnglish.isEnabled=ui.loaded && !ui.saving && sample==null
                    b.languageVietnamese.isEnabled=ui.loaded && !ui.saving && sample==null
                    b.languageError.isVisible=ui.error!=null || sample?.state=="languagefail"
                    b.languageRetry.isVisible=b.languageError.isVisible
                    b.languageRetry.isEnabled=!ui.saving
                    b.languageError.setText(if(ui.error==SettingsError.READ) R.string.settings_read_error else R.string.language_save_error)
                } }
                launch { display.state.collect { ui ->
                    b.displayCurrency.choose(ui.preferences.currency)
                    b.temperatureUnits.choose(unitLabels[if(ui.preferences.units=="celsius") 0 else 1])
                    b.displayCurrency.isEnabled=ui.loaded && !ui.saving && sample==null
                    b.temperatureUnits.isEnabled=ui.loaded && !ui.saving && sample==null
                    b.preferencesError.isVisible=ui.error!=null
                    b.preferencesRetry.isVisible=ui.error!=null
                    b.preferencesRetry.isEnabled=!ui.saving
                    b.preferencesError.setText(if(ui.error==SettingsError.READ) R.string.display_preferences_read_error else R.string.display_preferences_error)
                } }
            }
        }
    }
    private fun removeData() {
        val b=binding ?: return
        b.removeData.isEnabled=false
        viewLifecycleOwner.lifecycleScope.launch {
            val removed=workspace.discard(removeAll=true)
            if(binding==null) return@launch
            b.removeData.isEnabled=true
            Snackbar.make(b.root,if(removed) R.string.data_removed else R.string.remove_failed,if(removed) Snackbar.LENGTH_LONG else Snackbar.LENGTH_INDEFINITE)
                .apply { if(!removed) setAction(R.string.retry) { removeData() } }.show()
        }
    }
    override fun onDestroyView() { binding=null;super.onDestroyView() }
}
