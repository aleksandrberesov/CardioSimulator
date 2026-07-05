package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.Lead
import com.example.cardiosimulator.domain.TipLineEndCap
import com.example.cardiosimulator.domain.TipOverlayKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipsPanel(
    selectedKind: TipOverlayKind,
    onKindSelected: (TipOverlayKind) -> Unit,
    selectedEndCap: TipLineEndCap,
    onEndCapSelected: (TipLineEndCap) -> Unit,
    selectedLead: Lead?,
    onLeadSelected: (Lead?) -> Unit,
    onUndo: () -> Unit,
    onClearAll: () -> Unit,
    onEditComments: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(240.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.constructor_tips_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = stringResource(R.string.constructor_tips_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 4.dp)
            )

            HorizontalDivider()

            // Kind selection
            TipOverlayKind.entries.forEach { kind ->
                val label = when (kind) {
                    TipOverlayKind.Arrow -> stringResource(R.string.monitor_tips_type_arrow)
                    TipOverlayKind.LeadArea -> stringResource(R.string.monitor_tips_type_lead_area)
                    TipOverlayKind.GraphArea -> stringResource(R.string.monitor_tips_type_graph_area_rect)
                    TipOverlayKind.EcgPart -> stringResource(R.string.monitor_tips_type_ecg_part)
                    TipOverlayKind.VerticalLines -> stringResource(R.string.monitor_tips_type_vertical_lines)
                    TipOverlayKind.HorizontalLines -> stringResource(R.string.monitor_tips_type_horizontal_lines)
                    TipOverlayKind.Label -> stringResource(R.string.monitor_tips_type_label)
                    TipOverlayKind.FreeformArea -> stringResource(R.string.monitor_tips_type_freeform_area)
                    TipOverlayKind.Points -> stringResource(R.string.monitor_tips_type_points)
                }
                
                FilterChip(
                    selected = selectedKind == kind,
                    onClick = { onKindSelected(kind) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (selectedKind == TipOverlayKind.LeadArea) {
                HorizontalDivider()
                Text(stringResource(R.string.monitor_tips_lead_pick_header), style = MaterialTheme.typography.labelSmall)
                
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedLead?.name ?: "—",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor(),
                        textStyle = MaterialTheme.typography.bodySmall,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("—") },
                            onClick = { onLeadSelected(null); expanded = false }
                        )
                        Lead.entries.forEach { lead ->
                            DropdownMenuItem(
                                text = { Text(lead.name) },
                                onClick = { onLeadSelected(lead); expanded = false }
                            )
                        }
                    }
                }
            }

            if (selectedKind == TipOverlayKind.VerticalLines || selectedKind == TipOverlayKind.HorizontalLines) {
                HorizontalDivider()
                Text(stringResource(R.string.monitor_tips_line_cap_header), style = MaterialTheme.typography.labelSmall)
                
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    val capLabel = when (selectedEndCap) {
                        TipLineEndCap.Plain -> stringResource(R.string.monitor_tips_line_cap_plain)
                        TipLineEndCap.Dots -> stringResource(R.string.monitor_tips_line_cap_dots)
                        TipLineEndCap.Arrows -> stringResource(R.string.monitor_tips_line_cap_arrows)
                    }
                    OutlinedTextField(
                        value = capLabel,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor(),
                        textStyle = MaterialTheme.typography.bodySmall,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        TipLineEndCap.entries.forEach { cap ->
                            val label = when (cap) {
                                TipLineEndCap.Plain -> stringResource(R.string.monitor_tips_line_cap_plain)
                                TipLineEndCap.Dots -> stringResource(R.string.monitor_tips_line_cap_dots)
                                TipLineEndCap.Arrows -> stringResource(R.string.monitor_tips_line_cap_arrows)
                            }
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { onEndCapSelected(cap); expanded = false }
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onUndo, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.constructor_tips_undo), style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(onClick = onClearAll, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.constructor_tips_clear), style = MaterialTheme.typography.labelSmall)
                }
            }

            Button(onClick = onEditComments, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.constructor_tips_comments), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
