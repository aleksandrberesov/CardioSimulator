package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.EcgRepository
import com.example.cardiosimulator.data.EcgSource
import com.example.cardiosimulator.data.LocalPixelScale
import com.example.cardiosimulator.data.PixelScale
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.AppBuilder
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.OperatingMode
import com.example.cardiosimulator.domain.OperatingModeModel
import com.example.cardiosimulator.domain.SeriesScheme
import com.example.cardiosimulator.domain.bakeAnchorsToSamples
import com.example.cardiosimulator.ui.components.AnchorChart
import com.example.cardiosimulator.ui.components.CalibrationPulse
import com.example.cardiosimulator.ui.components.PaperGridLegend
import com.example.cardiosimulator.ui.components.PreviewPane
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.display.ekgGrid
import com.example.cardiosimulator.ui.display.leadArea
import com.example.cardiosimulator.ui.panels.EditorLeftPanel
import com.example.cardiosimulator.ui.panels.EditorRightPanel
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.EditorViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel

@Composable
fun EditorScreen(
    viewModel: AppViewModel,
    monitorViewModel: MonitorViewModel = viewModel(),
    rhythmViewModel: RhythmViewModel = viewModel(),
    editorViewModel: EditorViewModel = viewModel(),
    onLeaveAttempt: (() -> Unit)? = null,
) {
    val selectedRhythm by rhythmViewModel.selectedRhythm.collectAsState()
    val allSeries by rhythmViewModel.allSeries.collectAsState()
    val allParts by rhythmViewModel.allParts.collectAsState()

    val focusedLead by editorViewModel.focusedLead.collectAsState()
    val selectedPartIndex by editorViewModel.selectedPartIndex.collectAsState()
    val selectedAnchorIndex by editorViewModel.selectedAnchorIndex.collectAsState()
    val showSaveDialog by editorViewModel.showSaveDialog.collectAsState()

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
    val aMaxGlobal = focusedEditable?.aMax ?: focusedSeries?.aMax ?: 200
    val aValueGlobal = focusedEditable?.aValue ?: focusedSeries?.aValue ?: 2

    val bakedWaveform = remember(focusedEditable?.identy, anchors) {
        focusedEditable?.let { bakeAnchorsToSamples(it.anchors) }
    }

    val bakedPoints = remember(bakedWaveform) {
        Points(bakedWaveform?.samples ?: emptyList())
    }

    val density = LocalDensity.current
    val editorPixelScale = remember(
        aMaxGlobal, aValueGlobal, monitorMode.speed, monitorMode.scale, monitorMode.calibration, monitorMode.displayScale, density.density
    ) {
        val pxPerMm = density.density * (160f / 25.4f) * monitorMode.displayScale
        PixelScale.sourceAnchored(
            aMax = aMaxGlobal,
            aValue = aValueGlobal,
            paperSpeedMmPerSec = monitorMode.speed.toFloat(),
            gainZoomY = 1.0f,
            cal = monitorMode.calibration,
            physicalPxPerMm = pxPerMm
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { editorViewModel.setShowSaveDialog(false) },
            title = { Text(stringResource(R.string.editor_save_confirm_title)) },
            text = { Text(stringResource(R.string.editor_save_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveAll()
                    editorViewModel.setShowSaveDialog(false)
                    onLeaveAttempt?.invoke()
                }) { Text(stringResource(R.string.editor_save_yes)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.discardEdits()
                        editorViewModel.setShowSaveDialog(false)
                        onLeaveAttempt?.invoke()
                    }) { Text(stringResource(R.string.editor_save_no)) }
                    TextButton(onClick = { editorViewModel.setShowSaveDialog(false) }) {
                        Text(stringResource(R.string.editor_save_cancel))
                    }
                }
            }
        )
    }

    CompositionLocalProvider(LocalPixelScale provides editorPixelScale) {
        Row(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            EditorLeftPanel(
                viewModel = viewModel,
                rhythmViewModel = rhythmViewModel,
                editorViewModel = editorViewModel
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
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
                                        editorViewModel.setFocusedLead(lead)
                                    },
                                    backgroundColor = if (isSelected)
                                        MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                    modifier = Modifier.width(64.dp),
                                )
                            }
                        }

                        Monitor(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            monitorViewModel = monitorViewModel,
                            sourceAnchoredCalibration = aMaxGlobal to aValueGlobal,
                        ) { _, _ ->
                            Box(modifier = Modifier.fillMaxSize().ekgGrid()) {
                                if (focusedEditable != null) {
                                    Row(
                                        modifier = Modifier.leadArea(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(48.dp)
                                                .fillMaxHeight(),
                                        ) {
                                            CalibrationPulse(
                                                modifier = Modifier.fillMaxSize(),
                                                samplesPerMv = focusedEditable.samplesPerMv,
                                            )
                                            Text(
                                                text = focusedLead?.name ?: "",
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Serif,
                                                fontSize = 16.sp,
                                                color = Color.Black,
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .padding(top = 45.dp, start = 8.dp),
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight(),
                                        ) {
                                            if (bakedWaveform != null) {
                                                AnchorChart(
                                                    baked = bakedWaveform,
                                                    anchors = anchors,
                                                    sampleRateHz = focusedPart.effectiveSampleRateHz,
                                                    samplesPerMv = focusedEditable.samplesPerMv,
                                                    selectedIndex = selectedAnchorIndex,
                                                    onAnchorSelected = { editorViewModel.setSelectedAnchorIndex(it) },
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
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            }
                                            PaperGridLegend(
                                                paperSpeedMmPerSec = monitorMode.speed.toFloat(),
                                                gainMmPerMv = monitorMode.calibration.gainMmPerMv,
                                                modifier = Modifier.align(Alignment.TopStart),
                                            )
                                        }
                                    }
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
                    }
                }

                if (focusedEditable != null && focusedPart != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        PreviewPane(
                            points = bakedPoints,
                            sampleRateHz = focusedPart.effectiveSampleRateHz,
                            samplesPerMv = focusedEditable.samplesPerMv,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                        )
                    }
                }
            }

            EditorRightPanel(
                viewModel = viewModel,
                editorViewModel = editorViewModel,
                rhythmViewModel = rhythmViewModel
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 1000, heightDp = 600)
@Composable
fun EditorScreenPreview() {
    val appBuilder = AppBuilder()
    appBuilder.addMode(OperatingModeModel(OperatingMode.Editor))
    val appState = appBuilder.build()

    val dummySource = object : EcgSource {
        override fun listSeries() = emptyList<String>()
        override fun listParts() = emptyList<String>()
        override fun readSeries(name: String) = null
        override fun readPart(name: String) = null
    }
    val ecgRepository = EcgRepository(dummySource)

    CardioSimulatorTheme {
        EditorScreen(
            viewModel = remember { AppViewModel(appState = appState, ecgRepository = ecgRepository) },
            monitorViewModel = remember { MonitorViewModel() },
            rhythmViewModel = remember { RhythmViewModel(ecgRepository = ecgRepository) }
        )
    }
}
