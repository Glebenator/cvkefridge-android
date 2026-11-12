package com.cvke.fridge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.Change
import com.cvke.fridge.core.Inventory
import com.cvke.fridge.data.ChangeLogStore
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the screens show. */
sealed interface FridgeUiState {
    /** The first load is not complete. */
    data object Loading : FridgeUiState

    /** The change log could not be read. */
    data object LoadFailed : FridgeUiState

    /** The inventory, for the date [today]. */
    data class Ready(val content: InventoryContent, val today: CalendarDate) : FridgeUiState
}

/**
 * Reads the change log and gives the screens their content.
 *
 * @param today The date in the time zone of the device.
 */
class FridgeViewModel(
    private val changeLog: ChangeLogStore,
    private val today: () -> CalendarDate,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _state = MutableStateFlow<FridgeUiState>(FridgeUiState.Loading)
    val state: StateFlow<FridgeUiState> = _state.asStateFlow()

    private var changes: List<Change>? = null

    init {
        load()
    }

    /** Reads the change log again. The content on the screen stays until the new content replaces it. */
    fun load() {
        if (_state.value is FridgeUiState.LoadFailed) _state.value = FridgeUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val read = changeLog.read()
                changes = read
                ready(read)
            } catch (_: IOException) {
                FridgeUiState.LoadFailed
            } catch (_: IllegalArgumentException) {
                FridgeUiState.LoadFailed
            }
        }
    }

    /** Calculates the days left again when the date changed, for example after midnight. */
    fun refreshToday() {
        val shown = _state.value as? FridgeUiState.Ready ?: return
        val read = changes ?: return
        if (shown.today != today()) viewModelScope.launch { _state.value = ready(read) }
    }

    private suspend fun ready(changes: List<Change>): FridgeUiState.Ready = withContext(work) {
        val date = today()
        val sample = changes.any { it.origin == SAMPLE_ORIGIN }
        FridgeUiState.Ready(InventoryContent.of(Inventory.replay(changes), date, sample), date)
    }

    companion object {
        /** The origin of the changes that the debug build writes as a sample. No phone has this identifier. */
        const val SAMPLE_ORIGIN = "sample"
    }
}
