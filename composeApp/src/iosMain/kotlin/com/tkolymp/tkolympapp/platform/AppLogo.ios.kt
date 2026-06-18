package com.tkolymp.tkolympapp.platform

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode

@Composable
actual fun AppLogo(size: Dp, modifier: Modifier) {
    UIKitView(
        factory = {
            UIImageView(image = UIImage.imageNamed("AppIcon")).apply {
                contentMode = UIViewContentMode.UIViewContentModeScaleAspectFit
                clipsToBounds = true
            }
        },
        modifier = modifier.size(size)
    )
}
