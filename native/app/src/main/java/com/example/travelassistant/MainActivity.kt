package com.example.travelassistant

import android.app.LocaleManager
import android.os.Build
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.*
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.viewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.*
import com.example.travelassistant.core.domain.*
import com.example.travelassistant.databinding.ActivityMainBinding
import com.example.travelassistant.databinding.StartupErrorBinding
import com.example.travelassistant.core.ui.R as UiR
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.travelassistant.core.ui.WorkspaceViewModel
import com.example.travelassistant.core.ui.UiPreviewHost
import com.example.travelassistant.core.ui.previewTitle
import com.example.travelassistant.core.ui.screenTitle
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), UiPreviewHost {
    @Inject lateinit var languages: LanguageRepository
    @Inject lateinit var previewCatalog: UiPreviewCatalog
    private val workspace: WorkspaceViewModel by viewModels()
    private var localeReady=false
    private var navController: NavController?=null
    private var shell: ActivityMainBinding?=null
    private var startupJob: Job?=null
    private val primaryDestinations = setOf(UiR.id.home_fragment, UiR.id.flight_fragment, UiR.id.stay_fragment, UiR.id.trips_fragment)
    private lateinit var appBar: AppBarConfiguration
    override fun onCreate(state: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !localeReady }
        super.onCreate(state)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        restoreThenRender()
    }
    private fun platformTag(): String? = if (Build.VERSION.SDK_INT>=33) getSystemService(LocaleManager::class.java).applicationLocales[0]?.toLanguageTag() else null
    private fun restoreThenRender() {
        startupJob?.cancel()
        startupJob=lifecycleScope.launch {
            try {
                val language=languages.resolveStartup(platformTag(),Build.VERSION.SDK_INT>=33)
                applyLanguage(language)
                // The empty host is held behind the splash until its localized resources are ready.
                if(resources.configuration.locales[0].language!=language.tag) return@launch
                if(isFinishing || isDestroyed) return@launch
                if (shell == null) setupShell()
                localeReady=true
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    languages.settings.map { it.language }.distinctUntilChanged().collect { desired -> applyLanguage(desired) }
                }
            } catch(cancel: CancellationException) { throw cancel }
            catch(_: Exception) {
                localeReady=true
                shell?.let {
                    // A preferences error must not replace a working NavHost or its back stack.
                    Snackbar.make(it.root,UiR.string.settings_read_error,Snackbar.LENGTH_INDEFINITE)
                        .setAction(UiR.string.retry) { recreate() }.show()
                    return@launch
                }
                val error=StartupErrorBinding.inflate(layoutInflater)
                setContentView(error.root)
                applySystemInsets(error.root)
                error.retry.setOnClickListener { restoreThenRender() }
            }
        }
    }
    private suspend fun applyLanguage(language: AppLanguage) {
        if(AppCompatDelegate.getApplicationLocales().toLanguageTags()!=language.tag) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        }
        languages.markApplied(language,platformTag())
    }
    private fun setupShell() {
        val binding=ActivityMainBinding.inflate(layoutInflater)
        shell=binding
        setContentView(binding.root)
        applySystemInsets(binding.root)
        setSupportActionBar(binding.toolbar)
        val nav=(supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
        navController=nav
        appBar=AppBarConfiguration(setOf(UiR.id.home_graph, UiR.id.flight_graph, UiR.id.stay_graph, UiR.id.trips_graph))
        setupActionBarWithNavController(nav,appBar)
        binding.bottomNav.setupWithNavController(nav)
        nav.addOnDestinationChangedListener { _,destination,_ ->
            binding.bottomNav.isVisible=destination.id in primaryDestinations
            binding.toolbar.logo = if (destination.id == UiR.id.home_fragment) {
                androidx.appcompat.content.res.AppCompatResources.getDrawable(this, R.drawable.travel_toolbar_logo)
            } else {
                null
            }
            val sampleKey=nav.currentBackStackEntry?.arguments?.getString("previewCase")
            binding.toolbar.subtitle=if(previewCatalog.scenarios.any { it.key==sampleKey }) getString(UiR.string.preview_short) else null
            invalidateOptionsMenu()
        }
    }
    private fun applySystemInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view,insets ->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard=insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left,bars.top,bars.right,maxOf(bars.bottom,keyboard.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar,menu)
        return true
    }
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // Step 04 implements the contextual Settings return flow E27, including P03/I01.
        menu.findItem(R.id.open_settings)?.isVisible=navController?.currentDestination?.id?.let { it != UiR.id.settings_fragment } == true
        return super.onPrepareOptionsMenu(menu)
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId==R.id.open_settings && navController?.currentDestination?.id != UiR.id.settings_fragment) {
            navController?.navigate(UiR.id.settings_graph,null,androidx.navigation.navOptions { launchSingleTop=true })
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    override fun onSupportNavigateUp() = navController?.navigateUp(appBar) ?: super.onSupportNavigateUp()
    override fun onStop() { workspace.flushSoon(); super.onStop() }

    override fun openUiPreview() {
        val cases=previewCatalog.scenarios
        if(cases.isEmpty()) return
        val active=supportFragmentManager.findFragmentById(R.id.nav_host)?.childFragmentManager?.primaryNavigationFragment ?: return
        val labels=cases.map { "${getString(screenTitle(it.screen))} · ${active.previewTitle(it)} ${it.errorId}" }.toTypedArray()
        val routes=mapOf("H01" to R.id.preview_h01, "P01" to R.id.preview_p01, "P02" to R.id.preview_p02,
            "P03" to R.id.preview_p03, "P04" to R.id.preview_p04, "I01" to R.id.preview_i01, "I02" to R.id.preview_i02,
            "M01" to R.id.preview_m01, "M02" to R.id.preview_m02, "T01" to R.id.preview_t01,
            "T02" to R.id.preview_t02, "T03" to R.id.preview_t03, "T04" to R.id.preview_t04, "S01" to R.id.preview_s01)
        MaterialAlertDialogBuilder(this).setTitle(UiR.string.preview_gallery).setItems(labels) { _, index ->
            val selected=cases[index]
            val args=Bundle().apply {
                putString("previewCase",selected.key)
                putString("entryDestination",selected.screen)
                putLong("previewExpiry",System.currentTimeMillis()+86_400_000L)
            }
            navController?.navigate(requireNotNull(routes[selected.screen]),args,androidx.navigation.navOptions { launchSingleTop=true })
        }.setNegativeButton(UiR.string.close,null).show()
    }
}
