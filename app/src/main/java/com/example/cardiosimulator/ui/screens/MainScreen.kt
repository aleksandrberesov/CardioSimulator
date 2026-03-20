package com.example.cardiosimulator.ui.screens
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.cardiosimulator.ui.viewmodels.MainViewModel

@Composable
fun MainScreen(viewModel: MainViewModel){
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().topSection(),
            contentAlignment = Alignment.Center
        ) {
            Text("Top Section")
        }
        Row(modifier = Modifier.weight(10f).fillMaxWidth()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().middleSectionLeft(),
                contentAlignment = Alignment.Center
            ) { Text("Middle Left") }
            Box(
                modifier = Modifier.weight(3f).fillMaxHeight().middleSectionCenter(),
                contentAlignment = Alignment.Center
            ) { Text("Middle Center") }
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().middleSectionRight(),
                contentAlignment = Alignment.Center
            ) { Text("Middle Right") }
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().bottomSection(),
            contentAlignment = Alignment.Center
        ) {
            Text("Bottom Section")
        }
    }
}