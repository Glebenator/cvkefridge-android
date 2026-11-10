package com.cvke.fridge.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource

/** The 56 dp `accent-subtle` tile with a decorative icon in `accent-on-subtle`. */
@Composable
fun IconTile(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.size(Size.tileLg).background(CvkeFridgeTheme.colors.accentSubtle, RoundedCornerShape(Radius.lg)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, Modifier.size(Size.iconLg), tint = CvkeFridgeTheme.colors.accentOnSubtle)
    }
}
