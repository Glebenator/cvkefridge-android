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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A decorative icon on a rounded square: the [container] colour with the icon in the [content] colour. */
@Composable
fun IconTile(@DrawableRes icon: Int, container: Color, content: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(modifier.size(size).background(container, RoundedCornerShape(size * 0.3f)), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, Modifier.size(size * 0.5f), tint = content)
    }
}
