package com.cvke.fridge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.Change
import com.cvke.fridge.core.ChangeBody
import com.cvke.fridge.core.FoodTable
import com.cvke.fridge.core.Freshness
import com.cvke.fridge.core.Inventory
import com.cvke.fridge.core.RejectionReason
import com.cvke.fridge.data.ChangeLogStore
import com.cvke.fridge.data.PhoneId
import com.cvke.fridge.inventory.InventoryAction
import com.cvke.fridge.inventory.InventoryActions
import com.cvke.fridge.inventory.NewChanges
import com.cvke.fridge.inventory.NoticeMessage
import com.cvke.fridge.inventory.Planned
import com.cvke.fridge.lookup.LookupFailure
import com.cvke.fridge.lookup.LookupResult
import com.cvke.fridge.lookup.ProductLookup
import com.cvke.fridge.lookup.ScanSource
import com.cvke.fridge.lookup.Scanned
import com.cvke.fridge.lookup.ScannedFoods
import com.cvke.fridge.reminder.ReminderSettings
import com.cvke.fridge.reminder.Reminders
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the screens show. */
sealed interface FridgeUiState {
    /** The first load is not complete. */
    data object Loading : FridgeUiState

    /** The change log could not be read. */
    data object LoadFailed : FridgeUiState

    /**
     * The inventory, for the date [today].
     *
     * @property content What Home and My Fridge show.
     * @property inventory The replay of the change log. The other screens and the forms read it.
     * @property foods The table of foods. It is empty for a short time after the start, while the app reads it.
     * @property lastAreaId The storage area of the last batch that this phone added, or null.
     */
    data class Ready(
        val content: InventoryContent,
        val today: CalendarDate,
        val inventory: Inventory,
        val foods: FoodTable,
        val lastAreaId: String?,
    ) : FridgeUiState
}

/**
 * A message for the person after an action, for the snackbar.
 *
 * @property id A number that is different for each message.
 * @property undo The action that undoes the action of the message, or null.
 * @property retry The action to do again after a failed write, or null.
 */
data class Notice(val id: Long, val message: NoticeMessage, val undo: InventoryAction.Undo? = null, val retry: InventoryAction? = null)

/** The state of the product lookup of the scanner. */
sealed interface LookupState {
    data object Idle : LookupState

    data class Looking(val barcode: String) : LookupState

    /**
     * The add form can open with the values of the scan. The scanner opens it and then calls
     * [FridgeViewModel.lookupShown] with the [id].
     *
     * @property productId The product in the inventory with the barcode, or null.
     */
    data class Done(val id: Long, val scanned: Scanned, val productId: String?) : LookupState

    /** The lookup failed. The scanner keeps the [barcode] for "Try again" and "Enter by hand". */
    data class Failed(val barcode: String, val failure: LookupFailure) : LookupState
}

/**
 * A request to show My Fridge with a freshness filter, from a tap on the reminder notification. The tabs screen shows
 * it and then calls [FridgeViewModel.showRequestDone] with the [id].
 */
data class ShowRequest(val id: Long, val freshness: Set<Freshness>)

/** The state of the write of a form. The [token] names the form, so that a form does not read the result of another form. */
sealed interface FormWrite {
    data object Idle : FormWrite

    data class Saving(val token: String) : FormWrite

    /** The changes of the form are in the change log. */
    data class Saved(val token: String) : FormWrite

    /** Nothing was written. [reason] is the reason of the core, or null when the file could not be written. */
    data class Failed(val token: String, val reason: RejectionReason?) : FormWrite
}

/**
 * Reads the change log, gives the screens their content, and writes the changes of each action of the person.
 *
 * A write adds changes to the change log, and then the screens get the replay of the new log. The screens never show
 * a result that is not in the log.
 *
 * The view model also does the product lookup of the scanner. It runs in the scope of the view model, so it continues
 * when the screen turns, and [cancelLookup] stops it when the scanner closes.
 *
 * @param readFoods Reads the table of foods.
 * @param today The date in the time zone of the device.
 * @param productLookup The product lookup with its cache on the phone.
 * @param reminders The daily reminder: its settings, its alarm, and the notification.
 * @param now The instant of now, in milliseconds from 1970-01-01T00:00:00Z.
 * @param newId Makes a random identifier for a product, a batch, a storage area, or a change.
 */
class FridgeViewModel(
    private val changeLog: ChangeLogStore,
    private val phoneId: PhoneId,
    private val readFoods: suspend () -> FoodTable,
    private val today: () -> CalendarDate,
    private val productLookup: ProductLookup,
    private val reminders: Reminders,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _state = MutableStateFlow<FridgeUiState>(FridgeUiState.Loading)
    val state: StateFlow<FridgeUiState> = _state.asStateFlow()

    private val _notice = MutableStateFlow<Notice?>(null)

    /** The message that the snackbar shows, or null. */
    val notice: StateFlow<Notice?> = _notice.asStateFlow()

    private val _formWrite = MutableStateFlow<FormWrite>(FormWrite.Idle)
    val formWrite: StateFlow<FormWrite> = _formWrite.asStateFlow()

    private val _lookup = MutableStateFlow<LookupState>(LookupState.Idle)
    val lookup: StateFlow<LookupState> = _lookup.asStateFlow()
    private var lookupJob: Job? = null
    private var lookupCount = 0L

    /** The reminder settings, or null before the first read. */
    val reminderSettings: StateFlow<ReminderSettings?> = reminders.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _showRequest = MutableStateFlow<ShowRequest?>(null)

    /** The request of a tap on the reminder, or null. */
    val showRequest: StateFlow<ShowRequest?> = _showRequest.asStateFlow()
    private var showCount = 0L

    /** What the last read or write gave. One read or write runs at a time. */
    private class Loaded(val changes: List<Change>, val inventory: Inventory, val foods: FoodTable, val origin: String)

    private val mutex = Mutex()
    private var loaded: Loaded? = null

    /** The table of foods, after the first read. */
    private var foods: FoodTable? = null
    private var noticeCount = 0L

    init {
        load()
        // Sets the alarm again. Android removes it when the person stops the app in its settings.
        reminderAction { reminders.restore() }
    }

    /** Reads the change log again. The content on the screen stays until the new content replaces it. */
    fun load() {
        if (_state.value is FridgeUiState.LoadFailed) _state.value = FridgeUiState.Loading
        viewModelScope.launch {
            mutex.withLock {
                _state.value = try {
                    val origin = phoneId.get()
                    val changes = changeLog.read()
                    val inventory = withContext(work) { Inventory.replay(changes) }
                    val known = foods
                    if (known == null) {
                        // The inventory does not wait for the table of foods. The screens show it first, with an empty
                        // table, and get the table when it is read.
                        _state.value = ready(Loaded(changes, inventory, FoodTable(emptyList()), origin).also { loaded = it })
                    }
                    val table = known ?: readFoods().also { foods = it }
                    inventoryChanged(inventory)
                    ready(Loaded(changes, inventory, table, origin).also { loaded = it })
                } catch (_: IOException) {
                    FridgeUiState.LoadFailed
                } catch (_: IllegalArgumentException) {
                    FridgeUiState.LoadFailed
                }
            }
        }
    }

    /** Calculates the days left again when the date changed, for example after midnight. */
    fun refreshToday() {
        val shown = _state.value as? FridgeUiState.Ready ?: return
        if (shown.today == today()) return
        viewModelScope.launch { mutex.withLock { loaded?.let { _state.value = ready(it) } } }
    }

    /**
     * Does an action of the person: makes its changes, adds them to the change log, and gives the screens the new
     * inventory. The result of an action with a [formToken] is in [formWrite]. The result of the other actions, and
     * the message of each action, is in [notice]. The write continues when the screen that started it closes.
     */
    fun perform(action: InventoryAction, formToken: String? = null) {
        if (formToken != null) _formWrite.value = FormWrite.Saving(formToken)
        viewModelScope.launch {
            val result = mutex.withLock { write(action) }
            if (formToken != null) {
                _formWrite.value = when (result) {
                    is WriteResult.Done -> FormWrite.Saved(formToken)
                    is WriteResult.Refused -> FormWrite.Failed(formToken, result.reason)
                    WriteResult.Failed -> FormWrite.Failed(formToken, null)
                }
            }
            val notice = when (result) {
                is WriteResult.Done -> result.message?.let { message ->
                    Notice(++noticeCount, message, undo = InventoryAction.Undo(result.changeIds).takeIf { result.undoable })
                }
                // A form shows its failed write on the form.
                is WriteResult.Refused -> Notice(++noticeCount, NoticeMessage.Refused(result.reason)).takeIf { formToken == null }
                WriteResult.Failed -> Notice(++noticeCount, NoticeMessage.SaveFailed, retry = action).takeIf { formToken == null }
            }
            if (notice != null) _notice.value = notice
        }
    }

    /** The snackbar closed the message. */
    fun noticeShown(id: Long) = _notice.update { if (it?.id == id) null else it }

    /**
     * Finds the product of a scanned or typed [barcode], in the form of [com.cvke.fridge.lookup.Barcodes]. A product
     * in the inventory with the barcode comes first, with no network. Then the product lookup: the cache on the
     * phone, and then Open Food Facts. A new lookup replaces a lookup that runs.
     */
    fun lookUp(barcode: String) {
        lookupJob?.cancel()
        _lookup.value = LookupState.Looking(barcode)
        lookupJob = viewModelScope.launch {
            val ready = _state.value as? FridgeUiState.Ready
            val known = ready?.let { ScannedFoods.productWithBarcode(it.inventory.products, barcode) }
            _lookup.value = if (known != null) {
                LookupState.Done(++lookupCount, Scanned(barcode, ScanSource.IN_INVENTORY), known.id)
            } else {
                when (val result = productLookup.find(barcode)) {
                    is LookupResult.Found -> {
                        // The table of foods of the newest state, for the expiry estimate.
                        val foods = (_state.value as? FridgeUiState.Ready)?.foods ?: FoodTable(emptyList())
                        val scanned = withContext(work) { ScannedFoods.found(barcode, result.product, foods) }
                        LookupState.Done(++lookupCount, scanned, null)
                    }
                    LookupResult.NotFound -> LookupState.Done(++lookupCount, Scanned(barcode, ScanSource.NOT_FOUND), null)
                    is LookupResult.Failed -> LookupState.Failed(barcode, result.failure)
                }
            }
        }
    }

    /** Stops the lookup that runs, and forgets its result. */
    fun cancelLookup() {
        lookupJob?.cancel()
        lookupJob = null
        _lookup.value = LookupState.Idle
    }

    /** The scanner opened the add form for the result [id]. */
    fun lookupShown(id: Long) = _lookup.update { if (it is LookupState.Done && it.id == id) LookupState.Idle else it }

    /** The form read the result of its write. */
    fun formWriteRead(token: String) = _formWrite.update { current ->
        val done = current is FormWrite.Saved && current.token == token || current is FormWrite.Failed && current.token == token
        if (done) FormWrite.Idle else current
    }

    /** Turns reminders on or off. [fromOffer] is true for the offer on Home, which then says that reminders are on. */
    fun setRemindersOn(on: Boolean, fromOffer: Boolean = false) = reminderAction {
        reminders.setEnabled(on)
        if (on && fromOffer) _notice.value = Notice(++noticeCount, NoticeMessage.RemindersOn)
    }

    fun setReminderTime(minuteOfDay: Int) = reminderAction { reminders.setTime(minuteOfDay) }

    fun setReminderLeadDays(days: Int) = reminderAction { reminders.setLeadDays(days) }

    /** Home does not offer reminders again, and says where the switch is. */
    fun dismissReminderOffer() = reminderAction {
        reminders.dismissOffer()
        _notice.value = Notice(++noticeCount, NoticeMessage.RemindersInSettings)
    }

    /** The app asked Android for the notification permission. */
    fun notificationPermissionAsked() = reminderAction { reminders.permissionAsked() }

    /** Shows the reminder of today now. Only the debug build has a control for it. */
    fun sendTestReminder() = reminderAction {
        _notice.value = Notice(++noticeCount, NoticeMessage.TestReminder(reminders.sendTest()))
    }

    /** A tap on the reminder: My Fridge with the food that is expiring or expired. */
    fun showAttention() {
        _showRequest.value = ShowRequest(++showCount, setOf(Freshness.EXPIRING, Freshness.EXPIRED))
    }

    fun showRequestDone(id: Long) = _showRequest.update { if (it?.id == id) null else it }

    /** Runs a change of the reminder settings. A settings file that cannot be written gives a message. */
    private fun reminderAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (_: IOException) {
                _notice.value = Notice(++noticeCount, NoticeMessage.SettingsNotSaved)
            }
        }
    }

    /** Changes the reminder in the notification shade for the new inventory, after the screens have it. */
    private fun inventoryChanged(inventory: Inventory) {
        viewModelScope.launch {
            try {
                reminders.inventoryChanged(inventory)
            } catch (_: IOException) {
                // The reminder in the shade stays as it is. The next reminder reads the settings again.
            }
        }
    }

    private sealed interface WriteResult {
        data class Done(val changeIds: List<String>, val message: NoticeMessage?, val undoable: Boolean) : WriteResult

        data class Refused(val reason: RejectionReason) : WriteResult

        data object Failed : WriteResult
    }

    private suspend fun write(action: InventoryAction): WriteResult {
        val current = loaded ?: return WriteResult.Failed
        val date = today()
        val planned = when (val plan = InventoryActions.plan(action, current.inventory, date, newId)) {
            is Planned.Refused -> return WriteResult.Refused(plan.reason)
            is Planned.Changes -> plan
        }
        val added = NewChanges.stamp(planned.bodies, current.changes, now(), current.origin, newId)
        val changes = current.changes + added
        // The replay runs before the write. A change that the core rejects does not go into the change log.
        val inventory = withContext(work) { Inventory.replay(changes) }
        NewChanges.rejection(current.inventory, inventory)?.let { return WriteResult.Refused(it) }
        try {
            changeLog.append(added)
        } catch (_: IOException) {
            return WriteResult.Failed
        } catch (_: IllegalArgumentException) {
            return WriteResult.Failed
        }
        val next = Loaded(changes, inventory, current.foods, current.origin)
        loaded = next
        _state.value = ready(next)
        inventoryChanged(inventory)
        return WriteResult.Done(added.map(Change::id), planned.message, planned.undoable)
    }

    private suspend fun ready(loaded: Loaded): FridgeUiState.Ready = withContext(work) {
        val date = today()
        val sample = loaded.changes.any { it.origin == SAMPLE_ORIGIN }
        val areas = loaded.inventory.storageAreas.mapTo(HashSet()) { it.id }
        val lastAdded = loaded.changes
            .filter { it.origin == loaded.origin && it.body is ChangeBody.BatchAdded }
            .maxWithOrNull(compareBy<Change> { it.at }.thenBy { it.id })
        FridgeUiState.Ready(
            content = InventoryContent.of(loaded.inventory, date, sample),
            today = date,
            inventory = loaded.inventory,
            foods = loaded.foods,
            lastAreaId = (lastAdded?.body as? ChangeBody.BatchAdded)?.batch?.storageAreaId?.takeIf { it in areas },
        )
    }

    companion object {
        /** The origin of the changes that the debug build writes as a sample. No phone has this identifier. */
        const val SAMPLE_ORIGIN = "sample"
    }
}
