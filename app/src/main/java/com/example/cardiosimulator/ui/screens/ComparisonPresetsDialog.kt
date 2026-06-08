package com.example.cardiosimulator.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.ComparisonPreset
import com.example.cardiosimulator.ui.viewmodels.MonitorViewModel

@Composable
fun ComparisonPresetsDialog(
    monitorViewModel: MonitorViewModel,
    onDismiss: () -> Unit,
    onNewSchemaClick: () -> Unit,
    onPresetSelected: (ComparisonPreset) -> Unit
) {
    val monitorMode by monitorViewModel.monitorMode.collectAsState()
    val presets = monitorMode.comparisonPresets

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.7f)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.monitor_comparison_presets_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(presets) { preset ->
                        Text(
                            text = preset.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPresetSelected(preset) }
                                .padding(vertical = 16.dp, horizontal = 8.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.settings_close))
                    }
                    Button(onClick = onNewSchemaClick) {
                        Text(stringResource(R.string.monitor_comparison_presets_new))
                    }
                }
            }
        }
    }
}
