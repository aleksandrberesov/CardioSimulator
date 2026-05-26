package com.example.cardiosimulator.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SideDrawer(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    drawerContent: @Composable () -> Unit,
    handlerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    drawerWidth: Dp = 300.dp,
    drawerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    handlerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    handlerModifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxHeight(),
        verticalAlignment = verticalAlignment
    ) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandHorizontally(),
            exit = shrinkHorizontally()
        ) {
            Surface(
                modifier = Modifier
                    .width(drawerWidth)
                    .fillMaxHeight(),
                color = drawerColor,
                tonalElevation = 3.dp
            ) {
                drawerContent()
            }
        }

        // Handler
        Surface(
            modifier = Modifier
                .width(24.dp)
                .height(64.dp)
                .then(handlerModifier),
            color = handlerColor,
            shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
            tonalElevation = 3.dp,
            shadowElevation = 4.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onExpandedChange(!isExpanded) },
                contentAlignment = Alignment.Center
            ) {
                handlerContent()
            }
        }
    }
}
