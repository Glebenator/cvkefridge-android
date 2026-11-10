# Core rules

These are the product rules that both apps implement in their core modules: `:core` on Android and `CVKEFridgeCore`
on iOS. The files in `vectors/` give inputs and expected results for each rule. If this text and a vector disagree,
that is a defect in this folder. Correct it here first.

## 1. Values

- **Identifier.** A text of 1 to 64 characters from `a` to `z`, `0` to `9`, and `-`. Identifiers are compared
  character by character. The apps make random identifiers.
- **Calendar date.** A year, a month, and a day in the Gregorian calendar. The year is 1 to 9999. The text form is
  `YYYY-MM-DD` with 10 characters.
- **Instant.** A whole number of milliseconds from 1970-01-01T00:00:00Z.
- **Quantity.** A whole number of thousandths. `1500` is 1.5. The core does no arithmetic with fractions.
- **Unit.** `pcs`, `g`, `kg`, `ml`, `l`, `pack`, `bottle`, `slice`, `cup`, or none. The unit belongs to the product.
  All batches of a product have the same unit.
- **Category.** `produce`, `dairy`, `meat`, `condiment`, `beverage`, `bakery`, `leftovers`, or `other`.
- **Storage kind.** `pantry`, `fridge`, or `freezer`. Each storage area has one kind.

## 2. Calendar dates

- **Parse.** A text is a date only if it has exactly 10 characters, a `-` at positions 5 and 8, a digit at each other
  position, a year from 0001, a month from 01 to 12, and a day that the month has. February has 29 days in a year that
  divides by 4, but not in a year that divides by 100, unless the year divides by 400. No other text is a date. Spaces
  are not removed.
- **Add days.** A date plus a whole number of days is the date that many days later, or earlier for a negative number.
  If the result is before year 1 or after year 9999, there is no result.
- **Days between.** The days from date A to date B are positive when B is later.
- **Order.** An earlier date is less than a later date.
- **Date of an instant.** The date of an instant at a UTC offset in seconds is the day number
  `floor((milliseconds + offset * 1000) / 86400000)` counted from 1970-01-01. The division rounds down for negative
  numbers also. The app gives the instant and the offset of the device time zone at that instant.

## 3. Freshness

- **Days left** are the days from today to the expiry date. A batch with no expiry date has no days left.
- **Freshness** from the days left:

| Days left | Freshness |
|---|---|
| None | `noExpiryDate` |
| Below 0 | `expired` |
| 0, 1, or 2 | `expiring` |
| 3 or more | `fresh` |

## 4. Expiry estimate

- The table of foods is `foods.json`. Each food has an identifier, a name, a category, and a number of days for each
  storage kind in which the food can be kept. A storage kind can be absent.
- The estimated date for a food, a storage kind, and a date added is the date added plus the days for that kind. Its
  source is `estimated`.
- If the food is not in the table, or the food has no days for that kind, there is no estimate. The core does not use
  the category to make an estimate.

## 5. Batches and the use-first order

- A batch is **active** when its quantity is above 0. A batch with a quantity of 0 is **ended**.
- An expiry date has a source: `printed` or `estimated`. A batch has both the date and the source, or none of the two.
- The **use-first order** of active batches:
  1. A batch with an expiry date comes before a batch without one.
  2. The earlier expiry date comes first. The source of the date has no effect.
  3. Then the earlier date added comes first.
  4. Then the smaller batch identifier comes first.

## 6. Plan a use

To use or throw away a quantity of a product, the core makes a plan from the active batches of that product, in the
use-first order.

- The plan takes from the first batch. When that batch has not enough, the plan takes all of it and continues with the
  next batch.
- The **shortfall** is the quantity that the batches could not give. It is 0 when the batches had enough.
- A quantity of 0 or less gives an empty plan with a shortfall of 0.

## 7. The change log

The inventory is the result of the change log. A change has an identifier, an instant, the identifier of the phone
that made it, and a body with a type.

### 7.1 Replay

1. Sort the changes by instant, and then by identifier.
2. If two changes have the same identifier, keep the first and drop the others.
3. Find the changes that are undone (7.3).
4. Start with no products, no batches, no history, and the two fixed storage areas: `fridge` (name `Fridge`, kind
   `fridge`) and `freezer` (name `Freezer`, kind `freezer`).
5. Apply each change in order. Skip a change that is undone. If a change cannot apply, record it as rejected with its
   reason, and continue.

### 7.2 Change types

| Type | Fields | Effect | Rejected when |
|---|---|---|---|
| `productSaved` | `product` | Adds the product, or replaces the product with the same identifier. | Never |
| `productDeleted` | `productId` | Removes the product, its batches, and the history of those batches. | `unknownProduct` |
| `storageAreaSaved` | `area` (identifier, name, kind) | Adds the storage area, or replaces the one with the same identifier. | `fixedStorageArea`: the identifier is `fridge` or `freezer` |
| `storageAreaDeleted` | `storageAreaId` | Removes the storage area. | `fixedStorageArea`, `unknownStorageArea`, or `storageAreaInUse`: an active batch is in it. The checks are in this order. |
| `batchAdded` | `batch` | Adds the batch. | `duplicateBatch`, `unknownProduct`, `unknownStorageArea`, or `invalidQuantity`: the quantity is 0 or less. The checks are in this order. |
| `batchEdited` | `batchId`, `quantity`, `storageAreaId`, `dateAdded`, `expiryDate`, `expirySource` | Replaces these values of the batch. It makes no history. It can make an ended batch active again. | `unknownBatch`, `unknownStorageArea`, or `invalidQuantity`: the quantity is 0 or less. The checks are in this order. |
| `batchUsed` | `batchId`, `quantity`, `date` | Takes the quantity from the batch, but not more than the batch has. Adds a history entry with the outcome `used`, the quantity that was taken, and the date. | `unknownBatch`, `invalidQuantity`: the quantity is 0 or less, or `batchEnded`. The checks are in this order. |
| `batchThrownAway` | `batchId`, `quantity`, `date` | The same as `batchUsed`, with the outcome `thrownAway`. | The same as `batchUsed` |
| `batchDeleted` | `batchId` | Removes the batch and its history. A delete is for a mistake. | `unknownBatch` |
| `changeUndone` | `changeId` | See 7.3. | `unknownChange`, or `cannotUndo`: the other change is a `changeUndone`. The checks are in this order. |

A history entry has the identifier of its change, the batch, the product, the quantity, the outcome, and the date.
The history is in the order of the replay.

### 7.3 Undo

- A `changeUndone` that is not rejected makes the change that it names **undone**. Its position in the log has no
  effect: it can be before or after the change that it names.
- The replay skips an undone change. A skipped change is not in the rejected list.
- An undo cannot be undone. To get the same result again, the app makes a new change.

### 7.4 The result

The result of a replay has the products, the storage areas (each with `fixed`: true or false), all batches (active
and ended), the history, and the rejected changes. In the vectors, the products, the storage areas, and the batches
are in the order of their identifiers.
