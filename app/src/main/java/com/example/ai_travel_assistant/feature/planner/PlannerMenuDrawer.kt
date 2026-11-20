package com.example.ai_travel_assistant.feature.planner

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ai_travel_assistant.R

private const val DrawerWidthFraction = 0.8f
private val DrawerMaxWidth = 320.dp
private val DrawerLogoSize = 56.dp
private val DrawerItemSpacing = 4.dp

@Composable
internal fun PlannerMenuDrawer(
    drawerState: DrawerState,
    currentTab: HomeTab,
    onExploreClick: () -> Unit,
    onTripDetailsClick: () -> Unit,
    onLatestItineraryClick: (() -> Unit)?,
    onHistoryClick: () -> Unit,
    historyEnabled: Boolean,
) {
    val windowWidth = LocalConfiguration.current.screenWidthDp.dp
    val drawerWidth = minOf(windowWidth * DrawerWidthFraction, DrawerMaxWidth)
    ModalDrawerSheet(
        drawerState = drawerState,
        modifier = Modifier.width(drawerWidth).fillMaxHeight(),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.travel_brand_mark),
                    contentDescription = null,
                    modifier = Modifier.size(DrawerLogoSize),
                )
                Text(
                    text = stringResource(R.string.planner_brand),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(DrawerItemSpacing),
            ) {
                NavigationDrawerItem(
                    icon = { DrawerIcon(R.drawable.ic_trip_calendar) },
                    label = { Text(stringResource(R.string.planner_menu_trip_details)) },
                    selected = currentTab == HomeTab.Planner,
                    onClick = onTripDetailsClick,
                )
                NavigationDrawerItem(
                    icon = { DrawerIcon(R.drawable.ic_explore) },
                    label = { Text(stringResource(R.string.tab_explore)) },
                    selected = currentTab == HomeTab.Explore,
                    onClick = onExploreClick,
                )
                if (onLatestItineraryClick != null) {
                    NavigationDrawerItem(
                        icon = { DrawerIcon(R.drawable.ic_trip_itinerary) },
                        label = { Text(stringResource(R.string.planner_menu_latest_itinerary)) },
                        selected = false,
                        onClick = onLatestItineraryClick,
                    )
                }
                if (historyEnabled) {
                    NavigationDrawerItem(
                        icon = { DrawerIcon(R.drawable.ic_trip_history) },
                        label = { Text(stringResource(R.string.history_title)) },
                        selected = false,
                        onClick = onHistoryClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerIcon(@DrawableRes icon: Int) {
    Icon(painter = painterResource(icon), contentDescription = null)
}
