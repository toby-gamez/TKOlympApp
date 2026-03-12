package com.tkolymp.tkolympapp.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Platform-specific app logo image. */
@Composable
expect fun AppLogo(size: Dp = 80.dp, modifier: Modifier = Modifier)
