package com.nanodigest.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nanodigest.AppContainer
import com.nanodigest.ui.engine.EngineScreen
import com.nanodigest.ui.engine.EngineViewModel
import com.nanodigest.ui.history.HistoryDetailScreen
import com.nanodigest.ui.history.HistoryScreen
import com.nanodigest.ui.history.HistoryViewModel
import com.nanodigest.ui.summarize.SummarizeScreen
import com.nanodigest.ui.summarize.SummarizeViewModel

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    SUMMARIZE("summarize", "Summarize", Icons.Outlined.AutoAwesome),
    HISTORY("history", "History", Icons.Outlined.History),
    ENGINE("engine", "Engine", Icons.Outlined.Memory),
}

@Composable
private inline fun <reified VM : ViewModel> containerViewModel(
    container: AppContainer,
    crossinline create: (AppContainer) -> VM,
): VM = viewModel(factory = viewModelFactory { initializer { create(container) } })

@Composable
fun NanoDigestRoot(container: AppContainer) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Activity-scoped so they survive tab switches.
    val summarizeVm = containerViewModel(container) { SummarizeViewModel(it) }
    val historyVm = containerViewModel(container) { HistoryViewModel(it) }
    val engineVm = containerViewModel(container) { EngineViewModel(it) }

    // Text shared into the app always lands on the Summarize tab.
    val lastPosted by container.sharedText.lastPosted.collectAsStateWithLifecycle()
    LaunchedEffect(lastPosted) {
        val route = nav.currentDestination?.route
        // Before the graph exists we're about to start on Summarize anyway.
        if (lastPosted != 0L && route != null && route != Tab.SUMMARIZE.route) nav.selectTab(Tab.SUMMARIZE)
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in Tab.entries.map { it.route }) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { nav.selectTab(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.SUMMARIZE.route,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()).consumeWindowInsets(padding),
        ) {
            composable(Tab.SUMMARIZE.route) { SummarizeScreen(summarizeVm) }
            composable(Tab.HISTORY.route) { HistoryScreen(historyVm, onOpen = { nav.navigate("history/$it") }) }
            composable(Tab.ENGINE.route) { EngineScreen(engineVm) }
            composable("history/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                HistoryDetailScreen(
                    vm = historyVm,
                    id = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onReopen = { nav.selectTab(Tab.SUMMARIZE) },
                )
            }
        }
    }
}

private fun NavHostController.selectTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
