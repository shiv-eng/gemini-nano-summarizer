package com.nanodigest.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.nanodigest.data.IncomingText
import com.nanodigest.ui.components.readClipboard
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nanodigest.AppContainer
import com.nanodigest.ui.engine.EngineScreen
import com.nanodigest.ui.engine.EngineViewModel
import com.nanodigest.ui.history.HistoryDetailScreen
import com.nanodigest.ui.history.HomeScreen
import com.nanodigest.ui.history.HistoryViewModel
import com.nanodigest.ui.summarize.SummarizeScreen
import com.nanodigest.ui.summarize.SummarizeViewModel

private object Routes {
    const val HOME = "home"
    const val NEW = "new?fresh={fresh}"
    const val DIGEST = "digest/{id}"
    const val SETTINGS = "settings"

    fun new(fresh: Boolean) = "new?fresh=$fresh"
    fun digest(id: Long) = "digest/$id"
}

@Composable
private inline fun <reified VM : ViewModel> containerViewModel(
    container: AppContainer,
    crossinline create: (AppContainer) -> VM,
): VM = viewModel(factory = viewModelFactory { initializer { create(container) } })

/**
 * Notes-app structure: a home grid of digests, a "new digest" editor, a digest page and
 * settings. No tabs.
 */
@Composable
fun NanoDigestRoot(container: AppContainer) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val summarizeVm = containerViewModel(container) { SummarizeViewModel(it) }
    val historyVm = containerViewModel(container) { HistoryViewModel(it) }
    val engineVm = containerViewModel(container) { EngineViewModel(it) }

    // Text shared into the app (Share sheet, "Summarize" on selected text, app shortcut)
    // opens the editor, which picks it up and runs.
    val lastPosted by container.sharedText.lastPosted.collectAsStateWithLifecycle()
    LaunchedEffect(lastPosted) {
        // Runs after NavHost has set its graph. Each post opens the editor once.
        if (lastPosted != 0L && container.sharedText.navigatedFor != lastPosted) {
            container.sharedText.navigatedFor = lastPosted
            if (nav.currentDestination?.route != Routes.NEW) {
                nav.navigate(Routes.new(fresh = false)) { launchSingleTop = true }
            }
        }
    }

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                vm = historyVm,
                onOpen = { nav.navigate(Routes.digest(it)) },
                onNew = { nav.navigate(Routes.new(fresh = true)) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onSummarizeClipboard = {
                    val clip = readClipboard(context)
                    if (clip.isNullOrBlank() || clip.trim().length < 20) {
                        Toast.makeText(context, "Copy a chat or article first, or paste it here", Toast.LENGTH_LONG).show()
                        nav.navigate(Routes.new(fresh = true))
                    } else {
                        container.sharedText.post(IncomingText(clip, "Copied text", autoRun = true, userInitiated = true))
                    }
                },
            )
        }
        composable(Routes.NEW, arguments = listOf(navArgument("fresh") { type = NavType.BoolType; defaultValue = true })) { entry ->
            SummarizeScreen(
                vm = summarizeVm,
                fresh = entry.arguments?.getBoolean("fresh") ?: true,
                onClose = { nav.popBackStack() },
                onSaved = { id ->
                    nav.navigate(Routes.digest(id)) { popUpTo(Routes.HOME) }
                },
            )
        }
        composable(Routes.DIGEST, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            HistoryDetailScreen(
                vm = historyVm,
                id = entry.arguments?.getLong("id") ?: 0L,
                onBack = { nav.popBackStack() },
                onReopen = { nav.navigate(Routes.new(fresh = false)) },
            )
        }
        composable(Routes.SETTINGS) { EngineScreen(engineVm, onBack = { nav.popBackStack() }) }
    }
}
