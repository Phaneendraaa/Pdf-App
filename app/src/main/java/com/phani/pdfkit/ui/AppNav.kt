package com.phani.pdfkit.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.phani.pdfkit.Graph
import com.phani.pdfkit.data.DocFile
import com.phani.pdfkit.data.Settings
import com.phani.pdfkit.ui.components.ToastHost
import com.phani.pdfkit.ui.components.Toaster
import com.phani.pdfkit.ui.main.MainScreen
import com.phani.pdfkit.ui.main.Tab
import com.phani.pdfkit.ui.tools.ToolCatalog
import com.phani.pdfkit.ui.tools.ToolsScreen
import com.phani.pdfkit.util.FileShare

object Routes {
    const val MAIN = "main"
    const val TOOLS = "tools?focus={focus}"
    fun tools(focus: Boolean = false) = "tools?focus=$focus"
}

private const val DUR = 380

@Composable
fun PdfKitApp(settings: Settings) {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    val files by Graph.library.files.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }

    val openFile: (DocFile) -> Unit = { d -> FileShare.openExternal(ctx, d) }
    val onTool: (String) -> Unit = { key -> handleTool(key, nav) }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = Routes.MAIN,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(DUR)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(DUR), targetOffset = { it / 4 }) + fadeOut(tween(DUR)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(DUR), initialOffset = { it / 4 }) + fadeIn(tween(DUR)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(DUR)) },
        ) {
            composable(Routes.MAIN) {
                MainScreen(
                    files = files, settings = settings, tab = tab, onTab = { tab = it },
                    onTool = onTool, onSearch = { nav.navigate(Routes.tools(true)) },
                    onOpen = openFile, onTour = { Toaster.show("Welcome tour arrives in a later update") },
                )
            }
            composable(Routes.TOOLS, arguments = listOf(navArgument("focus") { type = NavType.BoolType; defaultValue = false })) { e ->
                ToolsScreen(
                    focusSearch = e.arguments?.getBoolean("focus") ?: false,
                    onBack = { nav.popBackStack() },
                    onTool = onTool,
                )
            }
        }
        ToastHost()
    }
}

private fun handleTool(key: String, nav: NavHostController) {
    when (key) {
        "more" -> nav.navigate(Routes.tools())
        else -> {
            val name = ToolCatalog.byKey(key)?.name ?: "This tool"
            Toaster.show("$name arrives in the next update")
        }
    }
}
