package com.example.cardiosimulator.ui.utils

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import kotlin.math.*

/**
 * Utility for extracting an ECG trace from a bitmap based on visual alignment.
 */
object TraceExtractor {

    /**
     * Scans the bitmap to find the darkest pixels in each column corresponding to an ADC sample.
     * Uses the inverse of the graphicsLayer transform to map view coordinates to bitmap pixels.
     */
    fun extract(
        bitmap: Bitmap,
        sampleCount: Int,
        baseline: Int,
        stepX: Float,
        stepY: Float,
        imageOffset: Offset,
        imageScale: Float,
        imageRotationDeg: Float,
        viewWidth: Float,
        viewHeight: Float
    ): IntArray {
        val result = IntArray(sampleCount)
        val baselineY = viewHeight / 2f
        
        val bitmapWidth = bitmap.width.toFloat()
        val bitmapHeight = bitmap.height.toFloat()

        // ContentScale.Fit logic (initial placement)
        val fitScale = min(viewWidth / bitmapWidth, viewHeight / bitmapHeight)
        val intrinsicX = (viewWidth - bitmapWidth * fitScale) / 2f
        val intrinsicY = (viewHeight - bitmapHeight * fitScale) / 2f

        // Pivot is center of the view for graphicsLayer
        val pivotX = viewWidth / 2f
        val pivotY = viewHeight / 2f

        // Pre-calculate rotation
        val rad = -imageRotationDeg * PI.toFloat() / 180f
        val cosR = cos(rad)
        val sinR = sin(rad)

        for (i in 0 until sampleCount) {
            val vx = i * stepX
            
            var bestValue = baseline
            var minLuminance = 1.0f

            // Scan vertically in view-space to find the darkest pixel in the bitmap
            // We scan +/- 300 pixels around baseline to save time
            val scanStart = (baselineY - 300).toInt().coerceAtLeast(0)
            val scanEnd = (baselineY + 300).toInt().coerceAtMost(viewHeight.toInt())

            for (vy in scanStart..scanEnd step 2) {
                // Inverse transform from view (vx, vy) to bitmap (bx, by)
                // 1. Move to pivot-relative
                val x1 = vx - pivotX
                val y1 = vy - pivotY
                // 2. Subtract user offset
                val x2 = x1 - imageOffset.x
                val y2 = y1 - imageOffset.y
                // 3. Un-rotate
                val x3 = x2 * cosR - y2 * sinR
                val y3 = x2 * sinR + y2 * cosR
                // 4. Un-scale
                val x4 = x3 / imageScale
                val y4 = y3 / imageScale
                // 5. Back to view-relative, then to bitmap-intrinsic
                val bxView = x4 + pivotX
                val byView = y4 + pivotY
                
                val bx = ((bxView - intrinsicX) / fitScale).roundToInt()
                val by = ((byView - intrinsicY) / fitScale).roundToInt()

                if (bx in 0 until bitmap.width && by in 0 until bitmap.height) {
                    val color = bitmap.getPixel(bx, by)
                    val luminance = (Color.red(color) * 0.2126f + 
                                   Color.green(color) * 0.7152f + 
                                   Color.blue(color) * 0.0722f) / 255f
                    
                    if (luminance < minLuminance) {
                        minLuminance = luminance
                        bestValue = (baseline + (baselineY - vy) / stepY).roundToInt()
                    }
                }
            }

            // Heuristic: if we found something dark enough, use it; otherwise stay near previous
            if (minLuminance < 0.7f) {
                result[i] = bestValue.coerceIn(0, 2048)
            } else {
                result[i] = if (i > 0) result[i-1] else baseline
            }
        }
        
        return result
    }
}
