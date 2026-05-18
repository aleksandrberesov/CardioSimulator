package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.domain.AnchorPoint
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.EditorViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel

@Composable
fun EditorRightPanel(
    viewModel: AppViewModel,
    editorViewModel: EditorViewModel,
    rhythmViewModel: RhythmViewModel,
    modifier: Modifier = Modifier,
) {
    val focusedLead by editorViewModel.focusedLead.collectAsState()
    val selectedAnchorIndex by editorViewModel.selectedAnchorIndex.collectAsState()
    val selectedRhythm by rhythmViewModel.selectedRhythm.collectAsState()
    val allSeries by rhythmViewModel.allSeries.collectAsState()
    val allParts by rhythmViewModel.allParts.collectAsState()
    val selectedPartIndex by editorViewModel.selectedPartIndex.collectAsState()

    val focusedSeriesId = selectedRhythm?.seriesIdentityByLead?.get(focusedLead)
    val focusedSeries = allSeries.find { it.identy == focusedSeriesId }
    val focusedParts = focusedSeries?.partRefs?.mapNotNull { ref ->
        allParts.find { it.identy == ref.partIdenty }
    } ?: emptyList()
    val focusedPart = selectedPartIndex?.let { focusedParts.getOrNull(it) }
    val focusedEditable = focusedPart?.let { viewModel.editablePart(it.identy) }

    Column(
        modifier = modifier
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
                editorViewModel.setSelectedAnchorIndex(null)
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
                    editorViewModel.setSelectedAnchorIndex(null)
                }
            },
            modifier = Modifier.weight(1f)
        )

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

        val es = focusedSeriesId?.let { viewModel.editableSeries(it) }
        SeriesInspector(
            series = es,
            onTitleChange = { v -> focusedSeriesId?.let { id -> viewModel.mutateSeries(id) { it.title = v } } },
            onDisplayNameChange = { v -> focusedSeriesId?.let { id -> viewModel.mutateSeries(id) { it.displayName = v } } },
            onPathologyChange = { v -> focusedSeriesId?.let { id -> viewModel.mutateSeries(id) { it.pathology = v } } },
            onParamsChange = { v -> focusedSeriesId?.let { id -> viewModel.mutateSeries(id) { it.params = v } } },
            modifier = Modifier.weight(1f)
        )
    }
}
