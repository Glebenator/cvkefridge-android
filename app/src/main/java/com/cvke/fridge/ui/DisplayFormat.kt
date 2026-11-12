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
