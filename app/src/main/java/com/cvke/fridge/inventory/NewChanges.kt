package com.cvke.fridge.inventory

import com.cvke.fridge.core.Change
import com.cvke.fridge.core.ChangeBody
import com.cvke.fridge.core.Inventory
import com.cvke.fridge.core.RejectionReason

/** Makes the changes that the phone adds to the change log, and checks them before the write. */
object NewChanges {
    /**
     * The changes for the [bodies], in their order. The replay sorts by the instant, so each change gets an instant
     * that is after all changes in the [log] and after the change before it. The instant is [now], or later when the
     * clock of the phone went back.
     */
    fun stamp(bodies: List<ChangeBody>, log: List<Change>, now: Long, origin: String, newId: () -> String): List<Change> {
        val last = log.maxOfOrNull { it.at }
        val first = if (last == null || last < now) now else last + 1
        return bodies.mapIndexed { index, body -> Change(newId(), first + index, origin, body) }
    }

    /**
     * Why the new changes cannot apply: the reason of the first change that the replay rejects in [next] and did not
     * reject in [previous]. It is null when the replay rejects no more changes than before.
     */
    fun rejection(previous: Inventory, next: Inventory): RejectionReason? {
        val before = previous.rejected.mapTo(HashSet()) { it.changeId }
        return next.rejected.firstOrNull { it.changeId !in before }?.reason
    }
}
