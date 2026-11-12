package com.cvke.fridge

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.ui.FridgeApp
import com.cvke.fridge.ui.FridgeViewModel
import com.cvke.fridge.ui.theme.CvkeFridgeTheme
import java.util.TimeZone
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app get() = application as FridgeApplication
    private val changeLog get() = app.changeLog

    private val viewModel: FridgeViewModel by viewModels {
        viewModelFactory { initializer { FridgeViewModel(changeLog, app.phoneId, app::readFoodTable, ::today) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) applyDebugRequest(intent)
        setContent {
            CvkeFridgeTheme { FridgeApp(viewModel) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyDebugRequest(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshToday()
    }

    /** The debug build can change the change log from an intent. The release build does nothing here. */
    private fun applyDebugRequest(intent: Intent) {
        lifecycleScope.launch { if (DebugRequests.apply(intent, changeLog, today())) viewModel.load() }
    }
}

/** The date of now in the time zone of the device. */
private fun today(): CalendarDate {
    val now = System.currentTimeMillis()
    return checkNotNull(CalendarDate.ofInstant(now, TimeZone.getDefault().getOffset(now) / 1000)) { "The clock is out of range" }
}
