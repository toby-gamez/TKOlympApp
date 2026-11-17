package com.tkolymp.tkolympapp.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.tkolymp.tkolympapp.ui.theme.BrandLightPrimary

/**
 * Drop-in replacement for Material's filled `Button` with the prototype look: pill shape, at least 50dp tall,
 * brand red container with white label (in both themes). Callers that pass their own `colors`/`shape` keep them.
 */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(26.dp),
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = BrandLightPrimary,
        contentColor = Color.White,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ),
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 50.dp),
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        content = content
    )
}
