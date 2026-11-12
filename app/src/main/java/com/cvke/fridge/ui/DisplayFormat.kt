package com.cvke.fridge.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.cvke.fridge.R
import com.cvke.fridge.core.Category
import com.cvke.fridge.core.ExpiryDate
import com.cvke.fridge.core.ExpirySource
import com.cvke.fridge.core.Freshness
import com.cvke.fridge.core.MeasureUnit
import com.cvke.fridge.core.RejectionReason
import com.cvke.fridge.core.StorageKind
import com.cvke.fridge.inventory.NameError
import com.cvke.fridge.inventory.TypedQuantity
import com.cvke.fridge.ui.theme.CvkeFridgeTheme
import java.math.BigDecimal
import java.text.NumberFormat

/** A whole number in the format of the locale. */
@Composable
fun numberFormat(): (Int) -> String {
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    return { format.format(it) }
}

/** A quantity in thousandths with its unit, for the locale of the device: "1.5 l", "2 packs", "3". */
@Composable
fun quantityText(quantity: Long, unit: MeasureUnit?): String {
    val locale = LocalConfiguration.current.locales[0]
    val number = remember(quantity, locale) {
        NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 3 }.format(BigDecimal.valueOf(quantity, 3))
    }
    // The plural form for one is for exactly 1. A quantity with a fraction uses the form for many.
    val count = if (quantity == 1000L) 1 else 2
    return when (unit) {
        null -> number
        MeasureUnit.PCS -> stringResource(R.string.unit_pcs, number)
        MeasureUnit.G -> stringResource(R.string.unit_g, number)
        MeasureUnit.KG -> stringResource(R.string.unit_kg, number)
        MeasureUnit.ML -> stringResource(R.string.unit_ml, number)
        MeasureUnit.L -> stringResource(R.string.unit_l, number)
        MeasureUnit.PACK -> pluralStringResource(R.plurals.unit_pack, count, number)
        MeasureUnit.BOTTLE -> pluralStringResource(R.plurals.unit_bottle, count, number)
        MeasureUnit.SLICE -> pluralStringResource(R.plurals.unit_slice, count, number)
        MeasureUnit.CUP -> pluralStringResource(R.plurals.unit_cup, count, number)
    }
}

/**
 * The days left, with the same words on the two screens: "Expired 3d ago", "Expired yesterday", "Expires today",
 * "Expires tomorrow", "Expires in 5d", or "No expiry". A date that the app suggested ends with ", estimated".
 */
@Composable
fun expiryText(daysLeft: Int?, expiry: ExpiryDate?): String {
    if (daysLeft == null || expiry == null) return stringResource(R.string.no_expiry)
    val text = when {
        daysLeft < -1 -> stringResource(R.string.expired_days_ago, -daysLeft)
        daysLeft == -1 -> stringResource(R.string.expired_yesterday)
        daysLeft == 0 -> stringResource(R.string.expires_today)
        daysLeft == 1 -> stringResource(R.string.expires_tomorrow)
        else -> stringResource(R.string.expires_in_days, daysLeft)
    }
    return if (expiry.source == ExpirySource.ESTIMATED) stringResource(R.string.expiry_estimated, text) else text
}

/** The status colour of a freshness, for text on a surface. Always show it with text. */
@Composable
fun freshnessColor(freshness: Freshness): Color = when (freshness) {
    Freshness.EXPIRED -> MaterialTheme.colorScheme.error
    Freshness.EXPIRING -> CvkeFridgeTheme.status.warning
    Freshness.FRESH -> CvkeFridgeTheme.status.success
    Freshness.NO_EXPIRY_DATE -> MaterialTheme.colorScheme.onSurfaceVariant
}

@StringRes
fun Category.label(): Int = when (this) {
    Category.PRODUCE -> R.string.category_produce
    Category.DAIRY -> R.string.category_dairy
    Category.MEAT -> R.string.category_meat
    Category.CONDIMENT -> R.string.category_condiment
    Category.BEVERAGE -> R.string.category_beverage
    Category.BAKERY -> R.string.category_bakery
    Category.LEFTOVERS -> R.string.category_leftovers
    Category.OTHER -> R.string.category_other
}

@DrawableRes
fun Category.icon(): Int = when (this) {
    Category.PRODUCE -> R.drawable.ic_carrot
    Category.DAIRY -> R.drawable.ic_cheese
    Category.MEAT -> R.drawable.ic_food_steak
    Category.CONDIMENT -> R.drawable.ic_bottle_soda
    Category.BEVERAGE -> R.drawable.ic_cup
    Category.BAKERY -> R.drawable.ic_bread_slice
    Category.LEFTOVERS, Category.OTHER -> R.drawable.ic_food_variant
}

@StringRes
fun SortKey.label(): Int = when (this) {
    SortKey.NAME -> R.string.sort_name
    SortKey.EXPIRY -> R.string.sort_expiry
    SortKey.DATE_ADDED -> R.string.sort_date_added
    SortKey.QUANTITY -> R.string.sort_quantity
    SortKey.CATEGORY -> R.string.sort_category
}

@StringRes
fun StorageKind.label(): Int = when (this) {
    StorageKind.PANTRY -> R.string.storage_kind_pantry
    StorageKind.FRIDGE -> R.string.storage_kind_fridge
    StorageKind.FREEZER -> R.string.storage_kind_freezer
}

/** The name of a unit in a list of units. Null is "no unit". */
@StringRes
fun unitName(unit: MeasureUnit?): Int = when (unit) {
    null -> R.string.unit_name_none
    MeasureUnit.PCS -> R.string.unit_name_pcs
    MeasureUnit.G -> R.string.unit_name_g
    MeasureUnit.KG -> R.string.unit_name_kg
    MeasureUnit.ML -> R.string.unit_name_ml
    MeasureUnit.L -> R.string.unit_name_l
    MeasureUnit.PACK -> R.string.unit_name_pack
    MeasureUnit.BOTTLE -> R.string.unit_name_bottle
    MeasureUnit.SLICE -> R.string.unit_name_slice
    MeasureUnit.CUP -> R.string.unit_name_cup
}

/** The problem of a quantity field, in words. */
@StringRes
fun TypedQuantity.Error.message(): Int = when (this) {
    TypedQuantity.Error.EMPTY -> R.string.error_quantity_empty
    TypedQuantity.Error.NOT_A_NUMBER -> R.string.error_quantity_not_a_number
    TypedQuantity.Error.TOO_MANY_DECIMALS -> R.string.error_quantity_decimals
    TypedQuantity.Error.NOT_POSITIVE -> R.string.error_quantity_not_positive
    TypedQuantity.Error.TOO_LARGE -> R.string.error_quantity_too_large
}

/** The problem of the name of a food, in words. */
@StringRes
fun NameError.foodMessage(): Int = when (this) {
    NameError.EMPTY -> R.string.error_name_empty
    NameError.TOO_LONG -> R.string.error_name_too_long
    NameError.IN_USE -> R.string.error_name_in_use
}

/**
 * Why the core does not accept a change, in words for the person. A screen checks most of these cases before the
 * write. The person sees the text when the inventory changed after the screen made its check.
 */
@StringRes
fun RejectionReason.message(): Int = when (this) {
    RejectionReason.UNKNOWN_PRODUCT -> R.string.refused_unknown_product
    RejectionReason.UNKNOWN_STORAGE_AREA -> R.string.refused_unknown_storage_area
    RejectionReason.UNKNOWN_BATCH -> R.string.refused_unknown_batch
    RejectionReason.UNKNOWN_CHANGE, RejectionReason.CANNOT_UNDO -> R.string.refused_cannot_undo
    RejectionReason.FIXED_STORAGE_AREA -> R.string.refused_fixed_storage_area
    RejectionReason.STORAGE_AREA_IN_USE -> R.string.refused_storage_area_in_use
    RejectionReason.INVALID_QUANTITY -> R.string.error_quantity_not_positive
    RejectionReason.DUPLICATE_BATCH -> R.string.refused_duplicate_batch
    RejectionReason.BATCH_ENDED -> R.string.refused_batch_ended
}
