package com.tkolymp.tkolympapp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkolymp.tkolympapp.platform.RemoteImage
import kotlin.math.absoluteValue

private val avatarPalette = listOf(
    Color(0xFF5C6BC0), // indigo
    Color(0xFF26A69A), // teal
    Color(0xFFEF5350), // red
    Color(0xFF66BB6A), // green
    Color(0xFFFFA726), // orange
    Color(0xFFAB47BC), // purple
    Color(0xFF29B6F6), // light blue
    Color(0xFFEC407A), // pink
    Color(0xFF8D6E63), // brown
    Color(0xFF78909C), // blue-grey
)

private fun colorForName(name: String): Color =
    avatarPalette[name.trim().hashCode().absoluteValue % avatarPalette.size]

private fun initialsOf(name: String): String =
    name.trim().split(" ", "-").filter { it.isNotBlank() }
        .take(2).joinToString("") { it.first().uppercaseChar().toString() }

/**
 * @param imageUrl profile photo; the initials stay as the placeholder while it loads or when it is null.
 * @param borderColor optional cohort/group colour shown as a ring around the circle.
 * @param borderWidth ring thickness; ignored when [borderColor] is null.
 */
@Composable
fun InitialsAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    fontSize: TextUnit = 9.sp,
    borderColor: Color? = null,
    borderWidth: Dp = 2.5.dp,
    imageUrl: String? = null,
) {
    val avatarBgColor = colorForName(name)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(borderColor ?: avatarBgColor)
            .then(if (borderColor != null) Modifier.padding(borderWidth) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        val innerMod = if (borderColor != null)
            Modifier.fillMaxSize().clip(CircleShape).background(avatarBgColor)
        else
            Modifier
        Box(modifier = innerMod, contentAlignment = Alignment.Center) {
            val initials = @Composable {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = initialsOf(name),
                        color = Color.White,
                        fontSize = fontSize,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (imageUrl != null) RemoteImage(imageUrl, Modifier.fillMaxSize(), fallback = initials) else initials()
        }
    }
}

/** Two overlapping circles for a couple: woman front-left, man back-right. */
@Composable
fun CoupleAvatar(
    womanName: String?,
    manName: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    fontSize: TextUnit = 9.sp,
    womanImageUrl: String? = null,
    manImageUrl: String? = null,
) {
    val overlap = size * 0.45f
    Box(modifier = modifier.size(size + overlap)) {
        if (manName != null) {
            InitialsAvatar(
                name = manName,
                size = size,
                fontSize = fontSize,
                imageUrl = manImageUrl,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
        if (womanName != null) {
            InitialsAvatar(
                name = womanName,
                size = size,
                fontSize = fontSize,
                imageUrl = womanImageUrl,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}
