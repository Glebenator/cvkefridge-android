package com.cvke.fridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cvke.fridge.ui.StartScreen
import com.cvke.fridge.ui.theme.CvkeFridgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CvkeFridgeTheme {
                StartScreen()
            }
        }
    }
}
