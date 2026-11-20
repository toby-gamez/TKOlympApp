package com.tkolymp.tkolympapp.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import com.tkolymp.tkolympapp.platform.rememberReduceMotion
import com.tkolymp.tkolympapp.util.Motion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OfflineBanner(isOffline: Boolean, message: String? = null) {
    val reduceMotion = rememberReduceMotion()
    AnimatedVisibility(
        visible = isOffline,
        enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(Motion.FadeMs)) + expandVertically(Motion.settleSpring()),
        exit = if (reduceMotion) ExitTransition.None else fadeOut(tween(Motion.FadeOutMs)) + shrinkVertically(tween(Motion.FadeMs))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message ?: "Offline — zobrazují se stažená data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}
