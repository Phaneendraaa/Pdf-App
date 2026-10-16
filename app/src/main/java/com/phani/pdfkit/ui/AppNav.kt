package com.phani.pdfkit.ui

import android.content.Context
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.phani.pdfkit.Graph
import com.phani.pdfkit.data.DocFile
import com.phani.pdfkit.data.Settings
import com.phani.pdfkit.pdf.ActiveJob
import com.phani.pdfkit.pdf.ImagesToPdf
import com.phani.pdfkit.pdf.JobResult
import com.phani.pdfkit.pdf.JpgDraft
import com.phani.pdfkit.pdf.Quality
import com.phani.pdfkit.pdf.rememberScanner
import com.phani.pdfkit.ui.ocr.OcrScreen
import com.phani.pdfkit.ui.components.ToastHost
import com.phani.pdfkit.ui.components.Toaster
import com.phani.pdfkit.ui.jpg.JpgToPdfScreen
import com.phani.pdfkit.ui.main.MainScreen
import com.phani.pdfkit.ui.main.Tab
import com.phani.pdfkit.ui.process.ProcessingScreen
import com.phani.pdfkit.ui.process.SuccessScreen
import com.phani.pdfkit.ui.tools.ToolCatalog
import com.phani.pdfkit.ui.tools.ToolsScreen
import com.phani.pdfkit.ui.tools.ToolRunScreen
import com.phani.pdfkit.ui.tools.RUNNABLE_TOOLS
import com.phani.pdfkit.ui.viewer.ViewerScreen

object Routes {
    const val MAIN = "main"
    const val TOOLS = "tools?focus={focus}"
    const val JPG = "jpg"
    const val PROCESSING = "processing"
    const val SUCCESS = "success"
    const val OCR = "ocr"
    const val VIEWER = "viewer?path={path}&tool={tool}"
    const val TOOL = "tool/{key}?path={path}"
    fun tool(key: String, path: String? = null) = "tool/$key" + (path?.let { "?path=${Uri.encode(it)}" } ?: "")
    fun tools(focus: Boolean = false) = "tools?focus=$focus"
    fun viewer(path: String, tool: String? = null) = "viewer?path=${Uri.encode(path)}&tool=${tool ?: ""}"
}

private const val DUR = 380

@Composable
fun PdfKitApp(settings: Settings) {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    val files by Graph.library.files.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }

    val openFile: (DocFile) -> Unit = { d ->
        if (d.isDoc) com.phani.pdfkit.util.FileShare.openExternal(ctx, d) else nav.navigate(Routes.viewer(d.path))
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val launchScanner = rememberScanner { job -> ActiveJob.result = job; nav.navigate(Routes.SUCCESS) { popUpTo(Routes.MAIN) } }
    var cameraExplain by androidx.compose.runtime.remember { mutableStateOf(false) }
    val startScan: () -> Unit = { if ("camera" in settings.hintsSeen) launchScanner() else cameraExplain = true }
    val onTool: (String) -> Unit = { key -> handleTool(key, nav, files, startScan) }

    // Files shared or opened from other apps, and launcher shortcuts.
    val incoming by com.phani.pdfkit.util.IncomingIntents.pending.collectAsState()
    androidx.compose.runtime.LaunchedEffect(incoming) {
        val i = incoming ?: return@LaunchedEffect
        com.phani.pdfkit.util.IncomingIntents.pending.value = null
        if (!settings.onboarded) { Graph.prefs.setOnboarded(true); Graph.prefs.setTourDone(true) }
        when (i) {
            is com.phani.pdfkit.util.Incoming.ViewPdf -> {
                val f = com.phani.pdfkit.util.Importer.toOpened(ctx, i.uri)
                if (f != null) nav.navigate(Routes.viewer(f.absolutePath)) else Toaster.show("Couldn't open that file")
            }
            is com.phani.pdfkit.util.Incoming.SharedPdfs -> {
                val got = com.phani.pdfkit.util.Importer.import(ctx, i.uris)
                when {
                    got.size == 1 -> nav.navigate(Routes.viewer(got[0].absolutePath))
                    got.size > 1 -> { tab = Tab.Files; Toaster.show("Added ${got.size} PDFs to Pdf Kit") }
                    else -> Toaster.show("Couldn't add those files")
                }
            }
            is com.phani.pdfkit.util.Incoming.SharedImages -> {
                JpgDraft.clear(); JpgDraft.images.addAll(i.uris)
                nav.navigate(Routes.JPG) { popUpTo(Routes.MAIN) }
            }
            is com.phani.pdfkit.util.Incoming.Tool -> onTool(i.key)
        }
    }

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
                    onOpen = openFile,
                    onTour = { tab = Tab.Home; scope.launch { Graph.prefs.setTourDone(false); Graph.prefs.setOnboarded(false) } },
                    showTour = settings.onboarded && !settings.tourDone,
                    onTourDone = { scope.launch { Graph.prefs.setTourDone(true) } },
                    draftCount = JpgDraft.images.size,
                    onResumeDraft = { nav.navigate(Routes.JPG) },
                    onDiscardDraft = { JpgDraft.clear() },
                )
            }
            composable(Routes.TOOLS, arguments = listOf(navArgument("focus") { type = NavType.BoolType; defaultValue = false })) { e ->
                ToolsScreen(
                    focusSearch = e.arguments?.getBoolean("focus") ?: false,
                    onBack = { nav.popBackStack() },
                    onTool = onTool,
                )
            }
            composable(Routes.JPG) {
                JpgToPdfScreen(
                    settings = settings,
                    onBack = { nav.popBackStack() },
                    onCreate = {
                        startImagesToPdf(ctx, settings)
                        nav.navigate(Routes.PROCESSING)
                    },
                )
            }
            composable(Routes.PROCESSING) {
                ProcessingScreen(
                    onCancel = { nav.popBackStack() },
                    onDone = { nav.navigate(Routes.SUCCESS) { popUpTo(Routes.MAIN) } },
                )
            }
            composable(Routes.SUCCESS) {
                SuccessScreen(
                    onDone = { nav.popBackStack(Routes.MAIN, inclusive = false) },
                    onOpen = openFile,
                    onAgain = { route ->
                        if (route == "scan") { nav.popBackStack(Routes.MAIN, false); startScan() } else {
                            if (route == Routes.JPG) JpgDraft.clear()
                            nav.navigate(route) { popUpTo(Routes.MAIN) }
                        }
                    },
                )
            }
            composable(
                Routes.VIEWER,
                arguments = listOf(
                    navArgument("path") { type = NavType.StringType; defaultValue = "" },
                    navArgument("tool") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { e ->
                ViewerScreen(
                    path = e.arguments?.getString("path").orEmpty(),
                    initialTool = e.arguments?.getString("tool")?.ifBlank { null },
                    onBack = { nav.popBackStack() },
                    onUnlock = { d -> nav.navigate(Routes.tool("unlock", d.path)) },
                    onReplace = { p -> nav.navigate(Routes.viewer(p)) { popUpTo(Routes.VIEWER) { inclusive = true } } },
                )
            }
            composable(Routes.OCR) { OcrScreen(onBack = { nav.popBackStack() }) }
            composable(
                Routes.TOOL,
                arguments = listOf(
                    navArgument("key") { type = NavType.StringType },
                    navArgument("path") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { e ->
                ToolRunScreen(
                    key = e.arguments?.getString("key").orEmpty(),
                    presetPath = e.arguments?.getString("path")?.ifBlank { null },
                    onBack = { nav.popBackStack() },
                    onStart = { nav.navigate(Routes.PROCESSING) },
                    onEdit = { p -> nav.navigate(Routes.viewer(p, "edit")) { popUpTo(Routes.MAIN) } },
                )
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = !settings.onboarded,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(tween(400)) + androidx.compose.animation.slideOutVertically(tween(450)) { -it / 12 },
        ) {
            com.phani.pdfkit.ui.onboarding.OnboardingScreen(onFinish = { tab = Tab.Home; scope.launch { Graph.prefs.setOnboarded(true) } })
        }
        ToastHost()
    }

    if (cameraExplain) com.phani.pdfkit.ui.components.PkSheet({ cameraExplain = false }, "Scan with your camera") {
        val c = com.phani.pdfkit.ui.theme.Pk.colors
        androidx.compose.material3.Text(
            "Pdf Kit uses Google's on-device document scanner. It finds page edges, fixes the angle and cleans up the image. " +
                "Photos are processed on your phone and saved only inside Pdf Kit.",
            color = c.ink2, fontSize = 15.sp, lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        com.phani.pdfkit.ui.components.PrimaryButton("Continue", Modifier.padding(top = 18.dp)) {
            cameraExplain = false
            scope.launch { Graph.prefs.markHint("camera") }
            launchScanner()
        }
    }
}

private fun startImagesToPdf(ctx: Context, settings: Settings) {
    val uris = JpgDraft.images.toList()
    val opts = JpgDraft.options
    val quality = Quality.of(settings.compression)
    ActiveJob.set(
        title = "Creating PDF",
        heading = "Creating your PDF...",
        steps = listOf("Preparing images", "Optimizing pages", "Creating PDF file", "Finalizing..."),
    ) { report ->
        val out = Graph.library.newFile(Graph.library.stampedName("Images"))
        val pages = ImagesToPdf.create(ctx, uris, out, opts, quality, report)
        if (pages == 0) { out.delete(); null } else {
            JpgDraft.clear()
            JobResult(out, pages, again = Routes.JPG)
        }
    }
}

private fun handleTool(key: String, nav: NavHostController, files: List<DocFile>, startScan: () -> Unit) {
    if (key.startsWith("edit:")) { nav.navigate(Routes.viewer(key.removePrefix("edit:"), "edit")); return }
    when (key) {
        "scan" -> startScan()
        "ocr" -> nav.navigate(Routes.OCR)
        "more" -> nav.navigate(Routes.tools())
        "jpg2pdf" -> nav.navigate(Routes.JPG)
        in RUNNABLE_TOOLS -> nav.navigate(Routes.tool(key))
        "sign", "text" -> {
            val pdf = files.firstOrNull { it.isPdf }
            if (pdf != null) nav.navigate(Routes.viewer(pdf.path, key)) else Toaster.show("Import a PDF first, then open it to ${if (key == "sign") "sign" else "add text"}")
        }
        else -> {
            val name = ToolCatalog.byKey(key)?.name ?: "This tool"
            Toaster.show("$name arrives in the next update")
        }
    }
}
