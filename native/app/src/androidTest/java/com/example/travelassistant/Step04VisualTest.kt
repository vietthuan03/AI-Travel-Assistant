package com.example.travelassistant

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.travelassistant.core.domain.*
import com.example.travelassistant.core.ui.WorkspaceViewModel
import com.example.travelassistant.core.ui.R as UiR
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.time.LocalDate

/** Native captures, not browser wireframes. Writes only to this isolated emulator's Download folder. */
@RunWith(AndroidJUnit4::class)
class Step04VisualTest {
    private fun shell(command: String): String {
        val fd=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        val output=FileInputStream(fd.fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8) };fd.close()
        return output
    }
    private fun await(test: ()->Boolean) {
        val end=System.currentTimeMillis()+20_000
        while(System.currentTimeMillis()<end) { if(test()) return;Thread.sleep(75) }
        fail("Native visual case not ready")
    }
    private fun shot(name: String) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(200)
        shell("screencap -p /sdcard/Download/step04-ui-qa/$name.png")
    }
    @Test fun captureEnglishAndVietnameseAtNarrowLargeText() {
        val originalFont=shell("settings get system font_scale").trim().takeIf { it.toFloatOrNull()!=null } ?: "1.0"
        val originalSize=shell("wm size").lineSequence().firstOrNull { it.startsWith("Override size:") }?.substringAfter(":")?.trim()
        shell("mkdir -p /sdcard/Download/step04-ui-qa")
        val routes=mapOf("H01" to R.id.preview_h01,"P01" to R.id.preview_p01,"P02" to R.id.preview_p02,"P03" to R.id.preview_p03,"P04" to R.id.preview_p04,
            "I01" to R.id.preview_i01,"I02" to R.id.preview_i02,"M01" to R.id.preview_m01,"M02" to R.id.preview_m02,"T01" to R.id.preview_t01,"T02" to R.id.preview_t02,"T03" to R.id.preview_t03,"T04" to R.id.preview_t04,"S01" to R.id.preview_s01)
        val samples=mapOf("P04" to "failed","I01" to "candidate","I02" to "unknown","M01" to "saved","M02" to "normal")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var vm: WorkspaceViewModel?=null
                await { var ready=false;scenario.onActivity { vm=ViewModelProvider(it)[WorkspaceViewModel::class.java];ready=vm!!.state.value.loaded };ready }
                runBlocking { vm!!.discard(true);vm!!.startNew() }
                scenario.onActivity {
                    val start=LocalDate.now().plusDays(5)
                    vm!!.edit { PlannerDraft(destinationId="danang-vn",destinationQuery="Da Nang",startDate=start.toString(),endDate=start.plusDays(2).toString(),adults="2",children="1",childBands="3_11",budget="1500",currency="USD",transport="excluded",purpose="leisure",notes="Prefer a relaxed pace and rest breaks.") }
                }
                runBlocking { vm!!.flush() }
                for(language in listOf("en","vi")) for(narrow in listOf(false,true)) {
                    val profile=if(narrow) "narrow200" else "standard100"
                    shell(if(narrow) "wm size 880x1760" else "wm size reset")
                    shell("settings put system font_scale "+if(narrow) "2.0" else "1.0")
                    var repo: LanguageRepository?=null
                    scenario.onActivity { repo=it.languages }
                    runBlocking { repo!!.requestLanguage(AppLanguage.fromTag(language)) }
                    await { var ready=false;scenario.onActivity { ready=it.resources.configuration.locales[0].language==language && it.findViewById<View>(R.id.toolbar)?.isShown==true };ready }
                    for((screen,route) in routes) {
                        val sample=samples[screen]?.let { "$screen:$it" }.orEmpty()
                        scenario.onActivity {
                            (it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController.navigate(route,Bundle().apply {
                                putString("entryDestination",screen);putString("previewCase",sample);putLong("previewExpiry",System.currentTimeMillis()+86_400_000)
                            })
                        }
                        await {
                            var ready=false
                            scenario.onActivity {
                                val host=it.supportFragmentManager.findFragmentById(R.id.nav_host) as? NavHostFragment
                                val f=host?.childFragmentManager?.primaryNavigationFragment
                                ready=f?.isResumed==true && f.arguments?.getString("screenKey")==screen && f.view?.height?.let { h -> h>0 }==true
                            };ready
                        }
                        shot("${language}_${profile}_${screen}_top")
                        scenario.onActivity {
                            val root=(it.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).childFragmentManager.primaryNavigationFragment!!.requireView()
                            when(root) {
                                is androidx.recyclerview.widget.RecyclerView -> root.scrollToPosition((root.adapter?.itemCount ?: 1)-1)
                                is androidx.core.widget.NestedScrollView -> root.fullScroll(View.FOCUS_DOWN)
                                is android.widget.ScrollView -> root.fullScroll(View.FOCUS_DOWN)
                            }
                        }
                        shot("${language}_${profile}_${screen}_bottom")
                    }
                }
            }
        } finally {
            shell(if(originalSize==null) "wm size reset" else "wm size $originalSize")
            shell("settings put system font_scale $originalFont")
        }
    }
}
