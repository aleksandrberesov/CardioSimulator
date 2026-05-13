package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.data.PixelScale
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.AnchorClipboard
import com.example.cardiosimulator.domain.AnchorPoint
import com.example.cardiosimulator.domain.AppBuilder
import com.example.cardiosimulator.domain.EasingCurve
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.domain.SeriesScheme
import com.example.cardiosimulator.ui.components.AnchorEditableCanvas
import com.example.cardiosimulator.ui.components.PaperGridLegend
import com.example.cardiosimulator.ui.components.PreviewPane
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.components.chartArea
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.display.ekgGrid
import com.example.cardiosimulator.ui.panels.AnchorInspector
import com.example.cardiosimulator.ui.panels.RhythmChoosingPanel
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel

@Composable
fun EditorScreen(
    viewModel: AppViewModel,
    monitorViewModel: MonitorViewModel = viewModel(),
    onLeaveAttempt: (() -> Unit)? = null,
) {
    val rhythms by viewModel.rhythms.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val selectedRhythm by viewModel.selectedRhythm.collectAsState()
    val allSeries by viewModel.allSeries.collectAsState()
    val allParts by viewModel.allParts.collectAsState()
    val dirtyParts by viewModel.dirtyParts.collectAsState()
    val dirtySeries by viewModel.dirtySeries.collectAsState()

    var focusedLead by remember { mutableStateOf<Lead?>(Lead.II) }
    var selectedPartIndex by remember { mutableStateOf<Int?>(null) }
    var selectedAnchorIndex by remember { mutableStateOf<Int?>(null) }
    var partFilter by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        monitorViewModel.setSeriesCount(1)
        monitorViewModel.setSeriesScheme(SeriesScheme.OneColumn)
    }

    val focusedSeriesId = selectedRhythm?.seriesIdentityByLead?.get(focusedLead)
    val focusedSeries = allSeries.find { it.identy == focusedSeriesId }
    val focusedParts = focusedSeries?.partRefs?.mapNotNull { ref ->
        allParts.find { it.identy == ref.partIdenty }
    } ?: emptyList()
    val focusedPart = selectedPartIndex?.let { focusedParts.getOrNull(it) }
    val focusedEditable = focusedPart?.let { viewModel.editablePart(it.identy) }
    val anchors = focusedEditable?.anchors?.toList() ?: emptyList()

    val monitorMode by monitorViewModel.monitorMode.collectAsState()
    val density = LocalDensity.current
    val pxPerMm = density.density * (160f / 25.4f) * monitorMode.displayScale
    val aMaxGlobal = focusedEditable?.aMax ?: focusedSeries?.aMax ?: 200
    val aValueGlobal = focusedEditable?.aValue ?: focusedSeries?.aValue ?: 2

    val editorPixelScale = remember(
        pxPerMm,
        monitorMode.speed,
        monitorMode.scale,
        monitorMode.calibration,
        aMaxGlobal,
        aValueGlobal
    ) {
        PixelScale.sourceAnchored(
            aMax = aMaxGlobal,
            aValue = aValueGlobal,
            paperSpeedMmPerSec = monitorMode.speed.toFloat(),
            gainZoomY = monitorMode.scale,
            cal = monitorMode.calibration,
            physicalPxPerMm = pxPerMm,
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.editor_save_confirm_title)) },
            text = { Text(stringResource(R.string.editor_save_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveAll()
                    showSaveDialog = false
                    onLeaveAttempt?.invoke()
                }) { Text(stringResource(R.string.editor_save_yes)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.discardEdits()
                        showSaveDialog = false
                        onLeaveAttempt?.invoke()
                    }) { Text(stringResource(R.string.editor_save_no)) }
                    TextButton(onClick = { showSaveDialog = false }) {
                        Text(stringResource(R.string.editor_save_cancel))
                    }
                }
            }
        )
    }

    CompositionLocalProvider(LocalPixelScale provides editorPixelScale) {
        Row(
            modifier = Modifier.fillMaxSize().systemBarsPadding()
        ) {
            // Left rail: rhythm chooser
            Box(
                modifier = Modifier.weight(1f).middleSectionLeft(),
                contentAlignment = Alignment.TopStart,
            ) {
                RhythmChoosingPanel(
                    rhythms = rhythms,
                    selectedPathology = selectedRhythm?.pathology,
                    currentLanguage = selectedLanguage,
                    onRhythmSelect = { viewModel.selectRhythm(it.pathology) },
                )
            }

            // Center: lead-selector row + main canvas + parts toolbar
            Column(
                modifier = Modifier.weight(4f).middleSectionCenter(),
            ) {
                // Lead selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Lead.entries.forEach { lead ->
                        val isSelected = lead == focusedLead
                        Tab(
                            text = lead.name,
                            onClick = {
                                focusedLead = lead
                                selectedPartIndex = null
                                selectedAnchorIndex = null
                            },
                            backgroundColor = if (isSelected)
                                MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            modifier = Modifier.width(64.dp),
                        )
                    }
                }

                // Main canvas inside the editor-mode Monitor (source-anchored grid)
                Monitor(
                    modifier = Modifier.fillMaxWidth().weight(4f),
                    monitorViewModel = monitorViewModel,
                    sourceAnchoredCalibration = aMaxGlobal to aValueGlobal,
                ) { _, _ ->
                    Box(modifier = Modifier.fillMaxSize().ekgGrid()) {
                        PaperGridLegend(
                            paperSpeedMmPerSec = monitorMode.speed.toFloat(),
                            gainMmPerMv = monitorMode.calibration.gainMmPerMv,
                            modifier = Modifier.align(Alignment.TopStart),
                        )
                        if (focusedEditable != null && focusedPart != null && anchors.isNotEmpty()) {
                            AnchorEditableCanvas(
                                anchors = anchors,
                                sampleRateHz = focusedPart.effectiveSampleRateHz,
                                samplesPerMv = focusedEditable.samplesPerMv,
                                selectedIndex = selectedAnchorIndex,
                                onAnchorSelected = { selectedAnchorIndex = it },
                                onAnchorMoved = { idx, nx, ny ->
                                    viewModel.mutatePart(focusedEditable.identy) { ep ->
                                        if (idx in ep.anchors.indices) {
                                            ep.anchors[idx] = ep.anchors[idx].copy(x = nx, y = ny)
                                        }
                                    }
                                },
                                onAllAnchorsMoved = { dx, dy ->
                                    viewModel.mutatePart(focusedEditable.identy) { ep ->
                                        for (i in ep.anchors.indices) {
                                            val a = ep.anchors[i]
                                            ep.anchors[i] = a.copy(x = a.x + dx, y = a.y + dy)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize().chartArea(),
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Pick a rhythm and a part to start editing.")
                            }
                        }
                    }
                }

                // Parts row + add/copy/delete/filter
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        focusedParts
                            .filterIndexed { _, p ->
                                partFilter.isBlank() ||
                                        p.title.contains(partFilter, ignoreCase = true) ||
                                        p.identy.contains(partFilter, ignoreCase = true)
                            }
                            .forEachIndexed { index, part ->
                                val realIndex = focusedParts.indexOf(part)
                                val isSelected = selectedPartIndex == realIndex
                                Tab(
                                    text = part.title,
                                    onClick = {
                                        selectedPartIndex = realIndex
                                        selectedAnchorIndex = null
                                    },
                                    backgroundColor = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.width(100.dp),
                                )
                            }
                    }
                    // Toolbar: Save / Undo / Copy / Paste
                    OutlinedButton(
                        onClick = { viewModel.saveAll() },
                        enabled = dirtyParts.isNotEmpty() || dirtySeries.isNotEmpty(),
                    ) { Text(stringResource(R.string.editor_save)) }
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(
                        onClick = {
                            focusedEditable?.let {
                                viewModel.undoPart(it.identy)
                                selectedAnchorIndex = null
                            }
                        },
                        enabled = focusedEditable != null,
                    ) { Text(stringResource(R.string.editor_undo)) }
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(
                        onClick = {
                            focusedEditable?.let { AnchorClipboard.set(it.anchors.toList()) }
                        },
                        enabled = focusedEditable != null,
                    ) { Text("Copy") }
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(
                        onClick = {
                            val ep = focusedEditable ?: return@OutlinedButton
                            if (!AnchorClipboard.hasContent) return@OutlinedButton
                            viewModel.mutatePart(ep.identy) { p ->
                                p.anchors.clear()
                                p.anchors.addAll(AnchorClipboard.get())
                            }
                        },
                        enabled = focusedEditable != null && AnchorClipboard.hasContent,
                    ) { Text("Paste") }
                }

                // HR=60 preview pane — loops the currently-focused part so the
                // editor user can see the result of edits in real time.
                if (focusedEditable != null && focusedPart != null) {
                    val previewSamples = remember(focusedPart.identy, focusedPart.samples) {
                        Points(focusedPart.samples.map { (it - 1024f) })
                    }
                    PreviewPane(
                        points = previewSamples,
                        sampleRateHz = focusedPart.effectiveSampleRateHz,
                        samplesPerMv = focusedEditable.samplesPerMv,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }

            // Right rail: anchor inspector
            Box(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
                    .padding(start = 8.dp),
            ) {
                val sel = selectedAnchorIndex?.let { idx ->
                    focusedEditable?.anchors?.getOrNull(idx)
                }
                AnchorInspector(
                    anchor = sel,
                    onEditX = { newX ->
                        val ep = focusedEditable ?: return@AnchorInspector
                        val idx = selectedAnchorIndex ?: return@AnchorInspector
                        viewModel.mutatePart(ep.identy) { p ->
                            if (idx in p.anchors.indices) p.anchors[idx] =
                                p.anchors[idx].copy(x = newX)
                        }
                    },
                    onEditY = { newY ->
                        val ep = focusedEditable ?: return@AnchorInspector
                        val idx = selectedAnchorIndex ?: return@AnchorInspector
                        viewModel.mutatePart(ep.identy) { p ->
                            if (idx in p.anchors.indices) p.anchors[idx] =
                                p.anchors[idx].copy(y = newY)
                        }
                    },
                    onCurveChange = { c ->
                        val ep = focusedEditable ?: return@AnchorInspector
                        val idx = selectedAnchorIndex ?: return@AnchorInspector
                        viewModel.mutatePart(ep.identy) { p ->
                            if (idx in p.anchors.indices) p.anchors[idx] =
                                p.anchors[idx].copy(curve = c)
                        }
                    },
                    onInsertBefore = {
                        val ep = focusedEditable ?: return@AnchorInspector
                        val idx = selectedAnchorIndex ?: return@AnchorInspector
                        viewModel.mutatePart(ep.identy) { p ->
                            if (idx in p.anchors.indices) {
                                val a = p.anchors[idx]
                                val prev = p.anchors.getOrNull(idx - 1)
                                val newAnchor = AnchorPoint(
                                    x = ((prev?.x ?: a.x) + a.x) / 2f,
                                    y = ((prev?.y ?: a.y) + a.y) / 2f,
                                    curve = a.curve,
                                )
                                p.anchors.add(idx, newAnchor)
                            }
                        }
                    },
                    onInsertAfter = {
                        val ep = focusedEditable ?: return@AnchorInspector
                        val idx = selectedAnchorIndex ?: return@AnchorInspector
                        viewModel.mutatePart(ep.identy) { p ->
                            if (idx in p.anchors.indices) {
                                val a = p.anchors[idx]
                                val next = p.anchors.getOrNull(idx + 1)
                                val newAnchor = AnchorPoint(
                                    x = (a.x + (next?.x ?: (a.x + 1f))) / 2f,
                                    y = (a.y + (next?.y ?: a.y)) / 2f,
                                    curve = a.curve,
                                )
                                p.anchors.add(idx + 1, newAnchor)
                            }
                        }
                    },
                    onDelete = {
                        val ep = focusedEditable ?: return@AnchorInspector
                        val idx = selectedAnchorIndex ?: return@AnchorInspector
                        viewModel.mutatePart(ep.identy) { p ->
                            if (idx in p.anchors.indices && p.anchors.size > 2) p.anchors.removeAt(
                                idx
                            )
                        }
                        selectedAnchorIndex = null
                    },
                    onCenterY = {
                        val ep = focusedEditable ?: return@AnchorInspector
                        val idx = selectedAnchorIndex ?: return@AnchorInspector
                        viewModel.mutatePart(ep.identy) { p ->
                            if (idx in p.anchors.indices) p.anchors[idx] =
                                p.anchors[idx].copy(y = 0f)
                        }
                    },
                    onUndo = {
                        focusedEditable?.let {
                            viewModel.undoPart(it.identy)
                            selectedAnchorIndex = null
                        }
                    },
                )
            }
        }
    }
}

    @Preview(showBackground = true, widthDp = 1000, heightDp = 600)
    @Composable
    fun EditorScreenPreview() {
        val appBuilder = AppBuilder()
        appBuilder.addMode(OperatingModeModel(OperatingMode.Editor))

        val previewViewModel: AppViewModel = viewModel(
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AppViewModel(
                        appState = appBuilder.build()
                    ) as T
                }
            }
        )

        CardioSimulatorTheme {
            EditorScreen(viewModel = previewViewModel)
        }
    }