package com.example.cardiosimulator.ui.panels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cardiosimulator.R
import com.example.cardiosimulator.domain.EcgPointType
import com.example.cardiosimulator.domain.SignificantPoint
import com.example.cardiosimulator.ui.utils.toDisplayString
import com.example.cardiosimulator.ui.screens.verticalScrollbar
import com.example.cardiosimulator.ui.viewmodels.ConstructorViewModel

@Composable
fun SignificantPointsDrawer(
    constructorViewModel: ConstructorViewModel,
    sampleRateHz: Float,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val targetFile by constructorViewModel.targetFile
    val selectedIndex by constructorViewModel.selectedIndex.collectAsState()
    val points = targetFile?.significantPoints?.sortedBy { it.index } ?: emptyList()

    Row(
        modifier = modifier.fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandHorizontally(),
            exit = shrinkHorizontally()
        ) {
            Surface(
                modifier = Modifier
                    .width(250.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 3.dp
            ) {
                SignificantPointsListPanel(
                    points = points,
                    selectedIndex = selectedIndex,
                    sampleRateHz = sampleRateHz,
                    onPointSelect = { constructorViewModel.selectIndex(it.index) }
                )
            }
        }

        // Handler
        Surface(
            modifier = Modifier
                .width(24.dp)
                .height(64.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
            tonalElevation = 3.dp,
            shadowElevation = 4.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { isExpanded = !isExpanded },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowLeft else Icons.Default.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "Hide points" else "Show points",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun SignificantPointsListPanel(
    points: List<SignificantPoint>,
    selectedIndex: Int,
    sampleRateHz: Float,
    onPointSelect: (SignificantPoint) -> Unit
) {
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        Text(
            text = stringResource(R.string.constructor_significant_points),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        HorizontalDivider()

        if (points.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.constructor_select_point_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScrollbar(listState),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(points) { point ->
                    val isSelected = point.index == selectedIndex
                    
                    Surface(
                        onClick = { onPointSelect(point) },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                            Column {
                                Text(
                                    text = point.type.toDisplayString(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.Black
                                )
                                val timeMs = (point.index * 1000f / sampleRateHz).toInt()
                                Text(
                                    text = stringResource(R.string.constructor_time_format, timeMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
