package com.example.cardiosimulator.ui.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.cardiosimulator.R

/**
 * Optional reference image painted at adjustable opacity behind the
 * editor canvas. Loading a custom image from a [Uri] requires an image
 * loader (Coil/Glide) — this component renders a transparent placeholder
 * when no painter is supplied, and renders the painter at [opacity]
 * otherwise. Wire to a future image-picker via [painter].
 *
 * `position` is reserved for persistence in `DataSourcePrefs`; current
 * implementation just fills the parent.
 */
@Composable
fun ReferenceOverlay(
    modifier: Modifier = Modifier,
    painter: Painter? = null,
    opacity: Float = 0.3f,
) {
    if (painter == null) return
    Image(
        painter = painter,
        contentDescription = null,
        modifier = modifier.fillMaxSize().alpha(opacity.coerceIn(0f, 1f)),
        contentScale = ContentScale.Fit,
    )
}
