package com.example.cardiosimulator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.cardiosimulator.ui.theme.CardioSimulatorTheme
import com.example.cardiosimulator.ui.screens.BaseSplitScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CardioSimulatorTheme {
                BaseSplitScreen()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun Preview() {
    CardioSimulatorTheme {
        BaseSplitScreen()
    }
}