package com.cvke.fridge.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cvke.fridge.core.Category
import com.cvke.fridge.photo.PhotoLoader
import com.cvke.fridge.photo.ProductPhoto
import com.cvke.fridge.ui.theme.IconTile

/** The loader of the product photos. With no loader, for example in a preview, the screens show the category icons. */
val LocalPhotoLoader = staticCompositionLocalOf<PhotoLoader?> { null }

/**
 * The photo for a box of [width] x [height] pixels, or null while it loads or when it cannot load. A photo in the
 * cache of the loader is there in the first frame, so a list that scrolls back does not show the icon first.
 */
@Composable
fun rememberPhoto(photo: ProductPhoto?, width: Int, height: Int): ImageBitmap? {
    val loader = LocalPhotoLoader.current
    if (photo == null || loader == null || width <= 0 || height <= 0) return null
    var bitmap by remember(photo, width, height) { mutableStateOf(loader.cached(photo, width, height)) }
    LaunchedEffect(photo, width, height) { if (bitmap == null) bitmap = loader.load(photo, width, height) }
    return bitmap
}

/**
 * The small picture of a product: its photo, or the icon of its category when it has no photo, in the same rounded
 * square. The photo is decorative, because the name of the food is next to it.
 */
@Composable
fun ProductTile(
    photo: ProductPhoto?,
    category: Category,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val pixels = with(LocalDensity.current) { size.roundToPx() }
    val bitmap = rememberPhoto(photo, pixels, pixels)
    if (bitmap == null) {
        IconTile(category.icon(), container, content, modifier, size)
    } else {
        Image(
            bitmap,
            contentDescription = null,
            modifier.size(size).clip(RoundedCornerShape(size * 0.3f)),
            contentScale = ContentScale.Crop,
        )
    }
}

/** The photo of a product at the size of the box of the [modifier], cropped to fill it. The [placeholder] colour shows while it loads. */
@Composable
fun ProductPhotoImage(photo: ProductPhoto, shape: Shape, placeholder: Color, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.clip(shape).background(placeholder)) {
        val bitmap = rememberPhoto(photo, constraints.maxWidth, constraints.maxHeight)
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}
