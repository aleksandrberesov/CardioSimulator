package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.ui.viewmodels.OskeViewModel
import com.example.cardiosimulator.ui.viewmodels.RhythmViewModel

@Composable
fun OskeConstructorControlPanel(
    oskeViewModel: OskeViewModel,
    rhythmViewModel: RhythmViewModel,
    modifier: Modifier = Modifier
) {
    val form by oskeViewModel.constructorForm.collectAsState()
    val specialty by oskeViewModel.constructorSpecialty.collectAsState()
    val ecgId by oskeViewModel.constructorSelectedEcgId.collectAsState()

    Row(
        modifier = modifier.fillMaxHeight().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = {
                if (ecgId != null) {
                    oskeViewModel.setupConstructor(specialty, ecgId!!)
                    rhythmViewModel.selectRhythm(ecgId!!)
                }
            },
            enabled = ecgId != null
        ) {
            Text("Edit Key")
        }

        if (form != null) {
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = { oskeViewModel.saveAnswerKey() }
            ) {
                Text(stringResource(R.string.constructor_save))
            }
        }
    }
}
