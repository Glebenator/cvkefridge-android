package com.cvke.fridge

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.reminder.ReminderNotifier
import com.cvke.fridge.ui.FridgeApp
import com.cvke.fridge.ui.FridgeViewModel
import com.cvke.fridge.ui.LocalPhotoLoader
import com.cvke.fridge.ui.theme.CvkeFridgeTheme
import java.util.TimeZone
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app get() = application as FridgeApplication
    private val changeLog get() = app.changeLog

    private val viewModel: FridgeViewModel by viewModels {
        viewModelFactory {
            initializer {
                FridgeViewModel(
                    changeLog,
                    app.phoneId,
                    app::readFoodTable,
                    ::today,
                    app.productLookup,
                    app.reminders,
                    app.photos,
                    app.photoEncoder,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            applyDebugRequest(intent)
            // A start from the list of recent apps gives the first intent again. It is not a new tap on the reminder.
            if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) applyReminderTap(intent)
        }
        val pickPhoto = app.photoPicker()
        setContent {
            CompositionLocalProvider(LocalPhotoLoader provides app.photoLoader) {
                CvkeFridgeTheme { FridgeApp(viewModel, pickPhoto) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyDebugRequest(intent)
        applyReminderTap(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshToday()
    }

    /** A tap on the reminder opens My Fridge with the food that is expiring or expired. */
    private fun applyReminderTap(intent: Intent) {
        if (intent.action == ReminderNotifier.ACTION_SHOW_ATTENTION) viewModel.showAttention()
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
