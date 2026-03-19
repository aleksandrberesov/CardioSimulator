package com.example.cardiosimulator.data

import android.content.Context
import com.example.cardiosimulator.R

data class Points(
    val values: List<Float>
) {
    companion object {
        fun fromResources(context: Context): Points {
            val rawString = context.getString(R.string.points_raw)
            val floatList = rawString.split(",")
                                     .mapNotNull { it.trim().toFloatOrNull() }
            return Points(values = floatList)
        }
    }
}
