package com.tkolymp.tkolympapp.platform

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tkolymp.tkolympapp.R

@Composable
actual fun AppLogo(size: Dp, modifier: Modifier) {
    Image(
        painter = painterResource(id = R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
