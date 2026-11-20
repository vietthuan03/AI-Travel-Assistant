package com.example.travelassistant.core.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.travelassistant.core.domain.*
import com.example.travelassistant.core.ui.databinding.FragmentMvpListBinding
import com.example.travelassistant.core.ui.databinding.ItemSectionBinding
import com.example.travelassistant.core.ui.databinding.ItemDayChipBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale
import kotlinx.coroutines.launch
import com.google.android.material.snackbar.Snackbar

data class UiAction(
    val label: String,
    val enabled: Boolean = true,
    val viewId: Int = View.NO_ID,
    val icon: Int = 0,
    val run: () -> Unit,
)

enum class TravelArtwork(@get:DrawableRes val drawable: Int) {
    Hero(R.drawable.travel_hero),
    Tools(R.drawable.travel_tools_art),
    EmptyTrips(R.drawable.travel_state_empty),
    Generating(R.drawable.travel_state_generating),
    Error(R.drawable.travel_state_error),
    Saved(R.drawable.travel_state_saved),
    Connection(R.drawable.travel_state_connection),
}

data class UiSection(val key: String, val title: String, val body: String = "", val badge: String = "",
    val primary: UiAction? = null, val secondary: UiAction? = null, val tertiary: UiAction? = null,
    val progress: Boolean = false, val choices: List<UiAction> = emptyList(), val selectedChoice: Int = 0,
    val artwork: TravelArtwork? = null)

abstract class MvpListFragment : Fragment() {
    private var binding: FragmentMvpListBinding? = null
    private var adapter: SectionAdapter? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentMvpListBinding.inflate(inflater, container, false).also { binding = it }.root
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = SectionAdapter()
        requireNotNull(binding).contentList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            // Status/draft acknowledgments must not briefly expose two copies of an action.
            itemAnimator = null
            adapter = this@MvpListFragment.adapter
            val edge = resources.getDimensionPixelSize(R.dimen.screen_inset)
            val maxWidth = resources.getDimensionPixelSize(R.dimen.content_max_width)
            addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
                val width = right - left
                if (width != oldRight - oldLeft) {
                    val inset = edge + ((width - maxWidth) / 2).coerceAtLeast(0)
                    setPaddingRelative(inset, edge, inset, edge)
                }
            }
        }
    }
    fun rows(sections: List<UiSection>) { adapter?.submitList(sections) }
    fun action(text: Int, enabled: Boolean = true, viewId: Int = View.NO_ID, icon: Int = 0, run: () -> Unit) =
        UiAction(getString(text), enabled, viewId, icon, run)
    protected fun sampleBanner() = UiSection("sample", getString(R.string.preview_short),getString(R.string.preview_banner),
        secondary = action(R.string.close) { findNavController().popBackStack() })
    override fun onDestroyView() {
        binding?.contentList?.adapter = null
        binding = null
        adapter = null
        super.onDestroyView()
    }
}

private class SectionAdapter : ListAdapter<UiSection, SectionAdapter.Holder>(object : DiffUtil.ItemCallback<UiSection>() {
    override fun areItemsTheSame(a: UiSection, b: UiSection) = a.key == b.key
    override fun areContentsTheSame(a: UiSection, b: UiSection) = a == b
}) {
    init { stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY }
    class Holder(val binding: ItemSectionBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemSectionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = with(holder.binding) {
        val item = getItem(position)
        val artwork = item.artwork
        val hero = artwork == TravelArtwork.Hero
        cover.isVisible = hero
        cover.setImageResource(if (hero) TravelArtwork.Hero.drawable else 0)
        illustration.isVisible = artwork != null && !hero
        illustration.setImageResource(artwork?.takeUnless { hero }?.drawable ?: 0)
        title.setTextAppearance(if (hero) R.style.TextAppearance_Travel_Hero else
            com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
        title.text = item.title
        body.text = item.body
        body.isVisible = item.body.isNotBlank()
        badge.text = item.badge
        badge.isVisible = item.badge.isNotBlank()
        progress.isVisible = item.progress
        dayChoices.removeAllViews()
        dayChoices.isVisible = item.choices.isNotEmpty()
        item.choices.forEachIndexed { index, choice ->
            val chip=ItemDayChipBinding.inflate(LayoutInflater.from(root.context),dayChoices,false).root
            chip.text=choice.label
            chip.fitLargeText()
            chip.isChecked=index==item.selectedChoice
            chip.setOnClickListener { choice.run() }
            dayChoices.addView(chip)
        }
        listOf(rowPrimary to item.primary, rowSecondary to item.secondary, rowTertiary to item.tertiary).forEach { (button, a) ->
            val defaultId=when(button) { rowPrimary -> R.id.row_primary; rowSecondary -> R.id.row_secondary; else -> R.id.row_tertiary }
            button.id=defaultId
            button.isVisible = a != null
            if (a != null) {
                button.id = if (a.viewId == View.NO_ID) defaultId else a.viewId
                button.text = a.label
                button.isEnabled = a.enabled
                button.setOnClickListener { a.run() }
            } else button.setOnClickListener(null)
            button.setIconResource(a?.icon ?: 0)
        }
    }
}

fun Fragment.navigateUi(source: Int, target: Int, args: Bundle? = null, topLevel: Boolean = false) {
    val nav = findNavController()
    if (!isResumed || nav.currentDestination?.id != source) return
    val entry=destinationEntry(target)
    val crossGraph=entry!=null && nav.currentDestination?.hierarchy?.none { it.id==entry.first }==true
    val routed=if(crossGraph) Bundle(args ?: Bundle()).apply { putString("entryDestination",entry!!.second) } else args
    nav.navigate(if(crossGraph) entry!!.first else target, routed, navOptions {
        launchSingleTop = true
        if (topLevel) {
            restoreState = true
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        } else {
            anim {
                enter = R.animator.travel_fade_enter
                exit = R.animator.travel_fade_exit
                popEnter = R.animator.travel_fade_enter
                popExit = R.animator.travel_fade_exit
            }
        }
    })
}
private fun destinationEntry(target: Int): Pair<Int,String>? = when(target) {
    R.id.destination_fragment -> R.id.home_graph to "ExploreDetail"
    R.id.where_fragment -> R.id.planner_graph to "P01"
    R.id.travelers_fragment -> R.id.planner_graph to "P02"
    R.id.review_fragment -> R.id.planner_graph to "P03"
    R.id.progress_fragment -> R.id.planner_graph to "P04"
    R.id.itinerary_fragment -> R.id.planner_graph to "I01"
    R.id.budget_fragment -> R.id.planner_graph to "I02"
    R.id.history_fragment -> R.id.trips_graph to "M02"
    R.id.currency_fragment -> R.id.tools_graph to "T02"
    R.id.time_fragment -> R.id.tools_graph to "T03"
    R.id.weather_fragment -> R.id.tools_graph to "T04"
    else -> null
}

/** Resolve child routes inside their graph, rather than navigating to an inactive graph's child. */
fun Fragment.redirectUiEntry(source: Int, screen: String, keepStart: Boolean = false): Boolean {
    val entry=arguments?.getString("entryDestination").orEmpty()
    if(entry.isEmpty() || entry==screen) return false
    val nav=findNavController()
    if(nav.currentDestination?.id!=source || nav.currentBackStackEntry?.savedStateHandle?.get<Boolean>("entryRouted")==true) return false
    val target=when(entry) {
        "ExploreDetail" -> R.id.destination_fragment
        "P02" -> R.id.travelers_fragment; "P03" -> R.id.review_fragment; "P04" -> R.id.progress_fragment
        "I01" -> R.id.itinerary_fragment; "I02" -> R.id.budget_fragment; "M02" -> R.id.history_fragment
        "T02" -> R.id.currency_fragment; "T03" -> R.id.time_fragment; "T04" -> R.id.weather_fragment
        else -> return false
    }
    nav.currentBackStackEntry?.savedStateHandle?.set("entryRouted",true)
    val args=Bundle(requireArguments()).apply { remove("entryDestination"); remove("screenKey") }
    nav.navigate(target,args,navOptions { launchSingleTop=true; if(!keepStart) popUpTo(source) { inclusive=true } })
    return true
}
fun Fragment.preview(catalog: UiPreviewCatalog): PreviewScenario? =
    catalog.scenarios.firstOrNull { it.key == arguments?.getString("previewCase") }

fun Fragment.previewArguments(key: String = arguments?.getString("previewCase").orEmpty()): Bundle = Bundle().apply {
    putString("previewCase", key)
    putLong("previewExpiry", arguments?.getLong("previewExpiry", 0L) ?: 0L)
}
fun Fragment.sampleAction() {
    MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.preview_action).setMessage(R.string.preview_dialog)
        .setPositiveButton(R.string.close, null).show()
}
fun Fragment.placeLabel(place: TravelPlace): String = "${place.name} · ${Locale("", place.country).getDisplayCountry(resources.configuration.locales[0])}"
fun AutoCompleteTextView.options(values: List<String>) { setAdapter(ArrayAdapter(context, R.layout.item_dropdown_choice, values)); threshold = 1 }
fun com.google.android.material.chip.Chip.fitLargeText() {
    chipMinHeight=maxOf(48*resources.displayMetrics.density,paint.fontMetricsInt.let { it.bottom-it.top }.toFloat()+24*resources.displayMetrics.density)
}
fun AutoCompleteTextView.choose(value: String) {
    if (text.toString() != value) {
        setText(value, false)
        // A restored/localized choice must not inherit an old horizontal scroll offset.
        setSelection(0)
        scrollTo(0,0)
    }
}
/** Numeric IME, preserving pasted input for validation instead of silently stripping signs/separators. */
fun android.widget.EditText.numericInput() {
    keyListener=android.text.method.TextKeyListener.getInstance()
    setRawInputType(android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED)
}

interface UiPreviewHost { fun openUiPreview() }

fun Fragment.openPlanner(workspace: WorkspaceViewModel, source: Int, resume: Boolean = false, destination: TravelPlace? = null) {
    val current = workspace.state.value
    if (!current.loaded || current.readFailed) return
    fun enter(discard: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (discard && !workspace.discard()) {
                Snackbar.make(requireView(), R.string.draft_failed, Snackbar.LENGTH_LONG).show()
                return@launch
            }
            workspace.startNew(destination)
            workspace.flush()
            val nav = findNavController()
            if (!isResumed || nav.currentDestination?.id != source) return@launch
            val step = workspace.state.value.draft?.step?.coerceIn(1, 3) ?: 1
            nav.navigate(R.id.planner_graph, null, navOptions { launchSingleTop = true })
            if (step >= 2) nav.navigate(R.id.travelers_fragment)
            if (step >= 3) nav.navigate(R.id.review_fragment)
        }
    }
    if (current.draft != null && !resume) {
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.unfinished_title).setMessage(R.string.unfinished_body)
            .setPositiveButton(R.string.resume_draft) { _, _ -> enter(false) }
            .setNegativeButton(R.string.discard_continue) { _, _ -> enter(true) }
            .setNeutralButton(R.string.cancel, null).show()
    } else enter(false)
}
