package com.example.cardiosimulator.ui.panels

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.Language
import com.example.cardiosimulator.ui.components.Tab
import com.example.cardiosimulator.ui.viewmodels.AppViewModel
import com.example.cardiosimulator.ui.viewmodels.ConstructorViewModel

@Composable
fun ConstructorTopPanel(
    appViewModel: AppViewModel,
    constructorViewModel: ConstructorViewModel,
    modifier: Modifier = Modifier,
) {
    val targetFile by constructorViewModel.targetFile
    val selectedLanguage by appViewModel.selectedLanguage.collectAsState()
    val isMetadataDirty by constructorViewModel.isMetadataDirty.collectAsState()
    val dirtyLeads by constructorViewModel.dirtyLeads.collectAsState()
    val isSaving by constructorViewModel.isSaving.collectAsState()

    val title = if (targetFile != null) {
        if (selectedLanguage == Language.RU) targetFile?.nameRu ?: targetFile?.titleEn else targetFile?.titleEn
    } else {
        stringResource(R.string.constructor_no_pathology_selected)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tab(
            text = title ?: "",
            onClick = { /* Could open rename dialog here */ },
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Tab(
            icon = Icons.Default.Add,
            text = stringResource(R.string.constructor_new_pathology),
            onClick = { constructorViewModel.createNewPathology() },
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        if (targetFile != null) {
            Tab(
                icon = Icons.Default.ContentCopy,
                iconContentDescription = stringResource(R.string.cd_copy),
                onClick = { constructorViewModel.duplicateCurrentPathology() },
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Tab(
                icon = Icons.Default.Save,
                iconContentDescription = stringResource(R.string.constructor_save),
                onClick = { constructorViewModel.save() },
                enabled = !isSaving && (isMetadataDirty || dirtyLeads.isNotEmpty()),
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Tab(
                icon = Icons.Default.Delete,
                iconContentDescription = stringResource(R.string.constructor_delete_confirm_title),
                onClick = { constructorViewModel.deleteCurrentPathology() },
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
