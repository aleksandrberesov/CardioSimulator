package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cardiosimulator.R
import com.example.cardiosimulator.data.Points
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.ui.components.SideDrawer
import com.example.cardiosimulator.ui.display.Lead as LeadView
import com.example.cardiosimulator.ui.display.LeadsGrid
import com.example.cardiosimulator.ui.display.Monitor
import com.example.cardiosimulator.ui.panels.RhythmSelector
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel

@Composable
fun TeachingScreen(
    appViewModel: AppViewModel,
    monitorViewModel: MonitorViewModel = viewModel(),
    rhythmViewModel: RhythmViewModel = viewModel(),
) {
    val rhythms by rhythmViewModel.rhythms.collectAsState()
    val selectedRhythm by rhythmViewModel.selectedRhythm.collectAsState()
    val waveforms by rhythmViewModel.waveforms.collectAsState()
    var isRhythmDrawerExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Column(
            modifier = Modifier.fillMaxSize().middleSectionCenter(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val selectedLanguage by appViewModel.selectedLanguage.collectAsState()
            val displayTitle = selectedRhythm?.let {
                if (selectedLanguage == Language.RU)
                    it.nameRu ?: it.titleEn
                else
                    it.titleEn
            } ?: stringResource(R.string.constructor_no_pathology_selected)

            Surface(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            val mode by monitorViewModel.monitorMode.collectAsState()
            Monitor(
                modifier = Modifier.weight(1f).padding(top = 8.dp, start = 24.dp),
                monitorViewModel = monitorViewModel,
            ) { rows, columns ->
                LeadsGrid(
                    rows = rows,
                    columns = columns,
                    itemCount = mode.count,
                ) { _, lead ->
                    val leadPoints = lead?.let { waveforms[it] }
                        ?.takeIf { it.values.size >= 2 }
                        ?: Points(emptyList<Float>())
                    LeadView(
                        points = leadPoints,
                        title = lead?.name ?: "",
                        isRunning = mode.isRunning
                    )
                }
            }
        }

        SideDrawer(
            isExpanded = isRhythmDrawerExpanded,
            onExpandedChange = { isRhythmDrawerExpanded = it },
            drawerWidth = 300.dp,
            drawerContent = {
                RhythmSelector(
                    appViewModel = appViewModel,
                    rhythms = rhythms,
                    selectedId = selectedRhythm?.id,
                    onRhythmSelect = { rhythmViewModel.selectRhythm(it.id) },
                )
            },
            handlerContent = {
                Text(
                    text = stringResource(R.string.rhythm_drawer_title),
                    modifier = Modifier
                        .requiredWidth(64.dp)
                        .rotate(-90f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            },
            modifier = Modifier.fillMaxHeight().align(Alignment.TopStart)
        )
    }
}
