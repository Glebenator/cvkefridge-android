package com.cvke.fridge.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.cvke.fridge.BuildConfig
import com.cvke.fridge.R
import com.cvke.fridge.core.AppVersion
import com.cvke.fridge.core.Freshness
import com.cvke.fridge.ui.theme.BrandSpinner
import com.cvke.fridge.ui.theme.BrandTile
import com.cvke.fridge.ui.theme.CvkeFridgeTheme
import com.cvke.fridge.ui.theme.IconTile
import com.cvke.fridge.ui.theme.Motion
import com.cvke.fridge.ui.theme.Size
import com.cvke.fridge.ui.theme.fadingEdges
import java.time.LocalTime

/**
 * Home, the first screen. Its contents are those of the Home screen of FridgeFriend (`app/(tabs)/index.tsx` in the
 * `basic_ff` repository): a greeting, a card with the totals, and the batches that need attention. The parts are
 * Material 3 components. The parts of FridgeFriend that need a function that this app does not have yet are not
 * here: the settings button, the sessions, and the add button. A tap on the number of expiring or expired batches
 * calls [onShowFreshness], and My Fridge then shows the food with that freshness.
 */
@Composable
fun HomeScreen(
    state: FridgeUiState,
    onViewAll: () -> Unit,
    onShowFreshness: (Freshness) -> Unit,
    onTryAgain: () -> Unit,
    insets: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val direction = LocalLayoutDirection.current
    val side = PaddingValues(
        start = insets.calculateStartPadding(direction) + 16.dp,
        end = insets.calculateEndPadding(direction) + 16.dp,
    )
    val content = (state as? FridgeUiState.Ready)?.content
    var about by rememberSaveable { mutableStateOf(false) }
    if (about) AboutSheet(onClose = { about = false })

    Column(
        modifier.fillMaxSize().background(scheme.surface).verticalScroll(rememberScrollState()).testTag("home_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The groups have their own padding at the sides, so that the row of cards scrolls to the edges of the screen.
        Column(
            Modifier
                .widthIn(max = Size.contentMax)
                .fillMaxWidth()
                .padding(top = insets.calculateTopPadding() + 24.dp, bottom = 24.dp),
        ) {
            Column(Modifier.padding(side)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting(), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.home_title),
                            Modifier.semantics { heading() },
                            color = scheme.onSurface,
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                    IconButton(onClick = { about = true }, Modifier.testTag("about_button")) {
                        Icon(
                            painterResource(R.drawable.ic_info),
                            contentDescription = stringResource(R.string.about),
                            tint = scheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                TotalsCard(content, onShowFreshness)
                if (content?.sample == true) SampleNote(Modifier.padding(top = 16.dp))
            }
            Spacer(Modifier.height(24.dp))
            if (state is FridgeUiState.LoadFailed) {
                LoadFailed(onTryAgain, Modifier.padding(side))
            } else {
                NeedsAttention(content, onViewAll, side)
            }
        }
    }
}

/** "Good morning" before 12:00, "Good afternoon" before 17:00, and then "Good evening", for the clock of the device. */
@Composable
private fun greeting(): String {
    var hour by remember { mutableIntStateOf(LocalTime.now().hour) }
    LifecycleResumeEffect(Unit) {
        hour = LocalTime.now().hour
        onPauseOrDispose {}
    }
    return stringResource(
        when {
            hour < 12 -> R.string.greeting_morning
            hour < 17 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        },
    )
}

/**
 * The card with the totals: the number of foods, and the numbers of expiring and expired batches. Each of the two
 * numbers of batches is a button when the inventory is loaded.
 */
@Composable
private fun TotalsCard(content: InventoryContent?, onShowFreshness: (Freshness) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val status = CvkeFridgeTheme.status
    val noValue = stringResource(R.string.hero_no_value)
    val number = numberFormat()
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.hero_label),
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        content?.let { number(it.foodCount) } ?: noValue,
                        Modifier.testTag("total_items"),
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
                // The icon of the product. It is the one place on Home with the signature gradient.
                BrandTile()
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = scheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Metric(
                    R.drawable.ic_clock,
                    content?.let { number(it.expiringCount) } ?: noValue,
                    stringResource(R.string.metric_expiring),
                    status.warning,
                    status.warningContainer,
                    status.onWarningContainer,
                    enabled = content != null,
                    onClick = { onShowFreshness(Freshness.EXPIRING) },
                    Modifier.weight(1f).testTag("metric_expiring"),
                )
                Metric(
                    R.drawable.ic_alert_outline,
                    content?.let { number(it.expiredCount) } ?: noValue,
                    stringResource(R.string.metric_expired),
                    scheme.error,
                    scheme.errorContainer,
                    scheme.onErrorContainer,
                    enabled = content != null,
                    onClick = { onShowFreshness(Freshness.EXPIRED) },
                    Modifier.weight(1f).testTag("metric_expired"),
                )
            }
        }
    }
}

/** One number of the card with its name, as a button. Screen readers hear the two as one item. */
@Composable
private fun Metric(
    @DrawableRes icon: Int,
    value: String,
    label: String,
    color: Color,
    container: Color,
    onContainer: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled, onClickLabel = stringResource(R.string.metric_show), role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, container, onContainer)
        Column {
            Text(value, color = color, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The group of the expired and the expiring batches, in a row that scrolls sideways. "View all" opens My Fridge. */
@Composable
private fun NeedsAttention(content: InventoryContent?, onViewAll: () -> Unit, side: PaddingValues, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier) {
        Row(Modifier.padding(side).padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.attention_title),
                Modifier.weight(1f).semantics { heading() },
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
            )
            TextButton(onViewAll, Modifier.testTag("view_all")) { Text(stringResource(R.string.attention_view_all)) }
        }
        // "All fresh" is for food that is there. An inventory with no food says so.
        val phase = when {
            content == null -> AttentionPhase.LOADING
            content.foodCount == 0 -> AttentionPhase.NO_ITEMS
            content.attention.isEmpty() -> AttentionPhase.ALL_FRESH
            else -> AttentionPhase.LIST
        }
        // The content comes in the place of the loading line with a fade. The cards and the numbers change at once.
        Crossfade(phase, animationSpec = tween(Motion.ENTER_MILLIS, easing = Motion.standard), label = "attention") { shown ->
            when (shown) {
            AttentionPhase.LOADING -> Notice(stringResource(R.string.loading), "loading", Modifier.padding(side)) { BrandSpinner() }
            AttentionPhase.NO_ITEMS -> EmptyState(stringResource(R.string.no_items), "no_items", Modifier.padding(side))
            AttentionPhase.ALL_FRESH -> Card(
                Modifier.padding(side).fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("all_fresh"),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(scheme.secondaryContainer, scheme.onSecondaryContainer),
            ) {
                Row(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, Modifier.size(32.dp))
                    Column {
                        Text(stringResource(R.string.attention_none_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            pluralStringResource(
                                R.plurals.attention_none_body,
                                Freshness.expiringWithinDays,
                                Freshness.expiringWithinDays,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            AttentionPhase.LIST -> {
                val scroll = rememberScrollState()
                Row(
                    Modifier
                        .fadingEdges(scroll, scheme.surface)
                        .horizontalScroll(scroll)
                        .padding(side)
                        .height(IntrinsicSize.Max)
                        .testTag("attention_list"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    content?.attention.orEmpty().forEach { AttentionCard(it, Modifier.fillMaxHeight()) }
                }
            }
            }
        }
    }
}

/** What the group of the batches that need attention shows. */
private enum class AttentionPhase { LOADING, NO_ITEMS, ALL_FRESH, LIST }

/**
 * About: the icon and the name of the product, its version, and the maker. The maker is "Made by" with the cvke
 * letters, as the design system of the owner says. The sheet comes up from the bottom edge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSheet(onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val version = remember { AppVersion(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE).label }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp).testTag("about"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandTile(size = 72.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.app_name),
                Modifier.semantics { heading() },
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.about_version, version),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(32.dp))
            Row(
                Modifier.semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.about_made_by),
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
                Image(
                    painterResource(R.drawable.cvke_letters),
                    contentDescription = stringResource(R.string.about_maker),
                    Modifier.width(72.dp),
                )
            }
        }
    }
}

/** One line in the place of the cards: the inventory loads, or it has no food. */
@Composable
private fun Notice(text: String, tag: String, modifier: Modifier = Modifier, leading: @Composable () -> Unit = {}) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier.fillMaxWidth().testTag(tag),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(scheme.surfaceContainerLow, scheme.onSurfaceVariant),
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * One batch that needs attention: the icon of its category, its name, its quantity, its category, and the days left.
 * The card has the error colours for an expired batch and the amber for an expiring batch, and the days left say the
 * same in words. Screen readers hear the card as one item.
 */
@Composable
private fun AttentionCard(batch: AttentionBatch, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val status = CvkeFridgeTheme.status
    val expired = batch.freshness == Freshness.EXPIRED
    val container = if (expired) scheme.errorContainer else status.warningContainer
    val onContainer = if (expired) scheme.onErrorContainer else status.onWarningContainer
    Card(
        modifier.width(180.dp).semantics(mergeDescendants = true) {}.testTag("attention_${batch.id}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(container, onContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            IconTile(batch.category.icon(), onContainer.copy(alpha = 0.1f), onContainer)
            Spacer(Modifier.height(12.dp))
            Text(batch.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            // "600 g • Meat"
            Text(
                "${quantityText(batch.quantity, batch.unit)} ${stringResource(R.string.meta_separator)} " +
                    stringResource(batch.category.label()),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(if (expired) R.drawable.ic_alert else R.drawable.ic_clock),
                    contentDescription = null,
                    Modifier.size(18.dp),
                )
                Text(expiryText(batch.daysLeft, batch.expiry), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
