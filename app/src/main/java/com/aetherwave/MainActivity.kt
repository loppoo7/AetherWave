package com.aetherwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aetherwave.core.designsystem.theme.AetherWaveTheme
import com.aetherwave.ui.navigation.AetherWaveApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AetherWaveTheme {
                AetherWaveApp()
            }
        }
    }
}
