package com.nexus.music.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Displays the original provider logo from drawable-nodpi when that resource exists. */
@Composable
fun PlatformArtworkBadge(platform: String) {
    val context = LocalContext.current
    val resourceId = remember(platform) {
        context.resources.getIdentifier("platform_${platform}", "drawable", context.packageName)
    }

    if (resourceId != 0) {
        Box(
            modifier = Modifier
                .padding(3.dp)
                .size(width = 25.dp, height = 18.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xF2FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(resourceId),
                contentDescription = "Платформа ${platform}",
                modifier = Modifier.size(width = 21.dp, height = 11.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}
