package com.example.ai_travel_assistant.feature.planner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai_travel_assistant.R
import kotlinx.coroutines.launch

private val PlannerLogoSize = 40.dp
private val ToolbarButtonSize = 48.dp

internal enum class HomeTab {
    Planner, Explore,
}

@Composable
internal fun PlannerScaffold(
    tab: HomeTab,
    selectTab: (HomeTab) -> Unit,
    onExit: () -> Unit,
    historyEnabled: Boolean,
    openHistory: () -> Unit,
    showTripDetails: () -> Unit,
    showLatestItinerary: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    val navigateFromMenu: (() -> Unit) -> Unit = { navigate ->
        drawerScope.launch {
            drawerState.close()
            navigate()
        }
    }

    var confirmExit by rememberSaveable { mutableStateOf(false) }
    BackHandler(
        enabled = drawerState.currentValue == DrawerValue.Closed &&
            drawerState.targetValue == DrawerValue.Closed && !confirmExit,
    ) {
        focusManager.clearFocus()
        keyboardController?.hide()
        when (tab) {
            HomeTab.Explore -> selectTab(HomeTab.Planner)
            HomeTab.Planner -> confirmExit = true
        }
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.app_exit_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    onExit()
                }) { Text(stringResource(R.string.app_exit_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) {
                    Text(stringResource(R.string.app_exit_stay))
                }
            },
        )
    }

    val plannerScroll = rememberScrollState()
    val exploreScroll = rememberScrollState()
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            PlannerMenuDrawer(
                drawerState = drawerState,
                currentTab = tab,
                onExploreClick = { navigateFromMenu { selectTab(HomeTab.Explore) } },
                onTripDetailsClick = { navigateFromMenu(showTripDetails) },
                onLatestItineraryClick = showLatestItinerary?.let { navigate -> { navigateFromMenu(navigate) } },
                onHistoryClick = { navigateFromMenu(openHistory) },
                historyEnabled = historyEnabled,
            )
        },
    ) {
        Scaffold(
            topBar = {
                PlannerTopBar(historyEnabled, openHistory) {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    drawerScope.launch { drawerState.open() }
                }
            },
            contentWindowInsets = WindowInsets.safeDrawing,
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
                    .pointerInput(focusManager, keyboardController) {
                        awaitEachGesture {
                            awaitFirstDown()
                            // Child controls and scroll gestures consume their own pointer changes.
                            if (waitForUpOrCancellation() != null) {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        }
                    }
                    .verticalScroll(if (tab == HomeTab.Planner) plannerScroll else exploreScroll)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    if (tab == HomeTab.Planner) PlannerHero()

                    content()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlannerTopBar(
    historyEnabled: Boolean,
    openHistory: () -> Unit,
    openMenu: () -> Unit,
) {
    Column {
        CenterAlignedTopAppBar(
            navigationIcon = {
                IconButton(
                    onClick = openMenu,
                    modifier = Modifier.size(ToolbarButtonSize),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_trip_menu),
                        contentDescription = stringResource(R.string.planner_open_menu),
                    )
                }
            },
            title = {
                Image(
                    painter = painterResource(R.drawable.travel_brand_mark),
                    contentDescription = stringResource(R.string.planner_brand),
                    modifier = Modifier.size(PlannerLogoSize),
                )
            },
            actions = {
                IconButton(
                    onClick = openHistory,
                    enabled = historyEnabled,
                    modifier = Modifier.size(ToolbarButtonSize),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_trip_history),
                        contentDescription = stringResource(R.string.planner_open_history),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun PlannerHero() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Image(
                painter = painterResource(R.drawable.travel_hero),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(2.75f),
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.planner_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.planner_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

