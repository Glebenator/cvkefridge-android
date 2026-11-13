package com.cvke.fridge.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.cvke.fridge.R
import com.cvke.fridge.photo.ProductPhoto
import com.cvke.fridge.core.Outcome
import kotlinx.coroutines.launch

/** What the person can do with a food from its card. */
enum class CardAction {
    /** Records all of the oldest batch as used or as thrown away. It is the action of a swipe. */
    USE_OLDEST,
    THROW_AWAY_OLDEST,

    /** Opens the dialog for a quantity. */
    USE,
    THROW_AWAY,

    /** Deletes the batches of the card, for a mistake. */
    DELETE,
}

/**
 * One food, as a Material list item on a card: the photo of the product or the icon of its category, its name, its
 * quantity, the days left of the batch to use first in the colour of its freshness, and its category. A food with
 * batches of different expiry dates says how many. A tap opens the screen of the food.
 *
 * The fast actions are on the card (`docs/research/competitor-apps.md` in the cvkefridge repository, sections 4 and
 * 8.2). A swipe to the end records the oldest batch as used. A swipe to the start records it as thrown away. The
 * two directions have different colours, icons, and words, and the person can undo each one. The menu of the card has
 * the same two actions with a quantity, for people who do not swipe, and screen readers have them as actions of the
 * card. "Delete" is only in the menu, below a line, because it is for a mistake.
 */
@Composable
fun FoodCard(row: FoodRow, photo: ProductPhoto?, onOpen: () -> Unit, onAction: (CardAction) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val swipe = rememberSwipeToDismissBoxState()
    val usedLabel = stringResource(R.string.action_used)
    val thrownAwayLabel = stringResource(R.string.action_thrown_away)
    SwipeToDismissBox(
        state = swipe,
        backgroundContent = {
            when (swipe.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd ->
                    SwipeBackground(R.drawable.ic_check, usedLabel, scheme.primaryContainer, scheme.onPrimaryContainer, Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart ->
                    SwipeBackground(R.drawable.ic_trash, thrownAwayLabel, scheme.tertiaryContainer, scheme.onTertiaryContainer, Alignment.CenterEnd)
                SwipeToDismissBoxValue.Settled -> {}
            }
        },
        modifier = modifier,
        onDismiss = { direction ->
            when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> onAction(CardAction.USE_OLDEST)
                SwipeToDismissBoxValue.EndToStart -> onAction(CardAction.THROW_AWAY_OLDEST)
                SwipeToDismissBoxValue.Settled -> {}
            }
            // The card comes back. It goes from the list when the new inventory has no batch for it.
            scope.launch { swipe.snapTo(SwipeToDismissBoxValue.Settled) }
        },
    ) {
        val status = freshnessColor(row.freshness)
        val separator = stringResource(R.string.meta_separator)
        val quantity = quantityText(row.quantity, row.unit)
        val expiry = expiryText(row.daysLeft, row.expiry)
        Card(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        ) {
            // The list item is one item for screen readers. The tap, the actions, and the tag of the card are on it.
            ListItem(
                modifier = Modifier
                    .clickable(onClick = onOpen)
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(usedLabel) {
                                onAction(CardAction.USE)
                                true
                            },
                            CustomAccessibilityAction(thrownAwayLabel) {
                                onAction(CardAction.THROW_AWAY)
                                true
                            },
                        )
                    }
                    .testTag("food_${row.key}"),
                headlineContent = { Text(row.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Column {
                        Text(
                            // "600 g • Expires in 3d": the quantity, a separator, and the days left in the colour of the freshness.
                            buildAnnotatedString {
                                append("$quantity $separator ")
                                withStyle(SpanStyle(color = status)) { append(expiry) }
                            },
                        )
                        if (row.expiryDateCount > 1) {
                            Text(pluralStringResource(R.plurals.expiry_date_count, row.expiryDateCount, row.expiryDateCount))
                        }
                    }
                },
                leadingContent = { ProductTile(photo, row.category, scheme.secondaryContainer, scheme.onSecondaryContainer) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(row.category.label()))
                        CardMenu(row, onAction)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

/** What shows behind a card while the person swipes it: the action in words, with its icon, at the side that opens. */
@Composable
private fun SwipeBackground(@DrawableRes icon: Int, label: String, container: Color, content: Color, alignment: Alignment) {
    Box(
        Modifier.fillMaxSize().background(container, MaterialTheme.shapes.large).padding(horizontal = 20.dp),
        contentAlignment = alignment,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, tint = content)
            Text(label, color = content, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The menu of a card: "Used" and "Thrown away" open the dialog for a quantity, and "Delete" is below a line. */
@Composable
private fun CardMenu(row: FoodRow, onAction: (CardAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, Modifier.testTag("food_menu_${row.key}")) {
            Icon(painterResource(R.drawable.ic_more), contentDescription = stringResource(R.string.food_more_actions, row.name))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TakeMenuItems(
                onUsed = {
                    open = false
                    onAction(CardAction.USE)
                },
                onThrownAway = {
                    open = false
                    onAction(CardAction.THROW_AWAY)
                },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = {
                    open = false
                    onAction(CardAction.DELETE)
                },
                modifier = Modifier.testTag("menu_delete"),
            )
        }
    }
}

/** The two menu items that record a quantity as used or as thrown away. Each has its icon. */
@Composable
fun TakeMenuItems(onUsed: () -> Unit, onThrownAway: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.action_used_quantity)) },
        onClick = onUsed,
        modifier = Modifier.testTag("menu_used"),
        leadingIcon = { Icon(painterResource(R.drawable.ic_check), contentDescription = null) },
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.action_thrown_away_quantity)) },
        onClick = onThrownAway,
        modifier = Modifier.testTag("menu_thrown_away"),
        leadingIcon = { Icon(painterResource(R.drawable.ic_trash), contentDescription = null) },
    )
}

/** The outcome that a [CardAction] records, or null for [CardAction.DELETE]. */
fun CardAction.outcome(): Outcome? = when (this) {
    CardAction.USE_OLDEST, CardAction.USE -> Outcome.USED
    CardAction.THROW_AWAY_OLDEST, CardAction.THROW_AWAY -> Outcome.THROWN_AWAY
    CardAction.DELETE -> null
}
