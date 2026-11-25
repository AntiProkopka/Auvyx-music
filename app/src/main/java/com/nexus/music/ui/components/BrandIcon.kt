package com.nexus.music.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import com.nexus.music.ui.theme.isGlass

@Composable
internal fun BrandIcon(@DrawableRes resource: Int, fallback: ImageVector, contentDescription: String?,
                       modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    if (isGlass) Icon(painterResource(resource), contentDescription, modifier, tint)
    else Icon(fallback, contentDescription, modifier, tint)
}
