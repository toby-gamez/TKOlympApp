package com.tkolymp.tkolympapp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tkolymp.shared.achievements.CampOccurrence
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.utils.formatMonthDay
import com.tkolymp.shared.viewmodels.DiplomaUiState
import com.tkolymp.tkolympapp.platform.AppLogo
import com.tkolymp.tkolympapp.platform.rememberShareImageCallback
import com.tkolymp.tkolympapp.ui.theme.AppTheme
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Full-screen dialog showing a completed camp's diploma, capturable and shareable as an image. */
@Composable
fun DiplomaDialog(
    diploma: DiplomaUiState,
    onDismiss: () -> Unit,
) {
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val strings = AppStrings.current.achievements
    val shareCallback = rememberShareImageCallback(
        fileBaseName = "diploma_${diploma.camp.eventId}",
        shareTitle = strings.diplomaShareButton,
    )
    var isSharing by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DiplomaCardContent(
                    diploma = diploma,
                    modifier = Modifier.drawWithContent {
                        graphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(graphicsLayer)
                    }
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isSharing,
                        modifier = Modifier.weight(1f)
                    ) { Text(AppStrings.current.commonActions.cancel) }

                    Button(
                        onClick = {
                            if (!isSharing) {
                                isSharing = true
                                scope.launch {
                                    val bitmap = graphicsLayer.toImageBitmap()
                                    shareCallback(bitmap)
                                    isSharing = false
                                }
                            }
                        },
                        enabled = !isSharing,
                        modifier = Modifier.weight(2f)
                    ) {
                        if (isSharing) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(18.dp).height(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(strings.diplomaShareButton)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiplomaCardContent(diploma: DiplomaUiState, modifier: Modifier = Modifier) {
    val strings = AppStrings.current.achievements
    val langCode = AppStrings.currentLanguage.code
    val bg = MaterialTheme.colorScheme.background
    val onBg = MaterialTheme.colorScheme.onBackground
    val accent = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .border(2.dp, accent, RoundedCornerShape(24.dp))
            .padding(32.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            AppLogo(
                size = 56.dp,
                modifier = Modifier.graphicsLayer({ colorFilter = ColorFilter.tint(onBg) })
            )
            Spacer(Modifier.height(20.dp))
            Text("🏕️", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text(strings.diplomaCertifies, fontSize = 14.sp, color = onBg.copy(alpha = 0.65f))
            Spacer(Modifier.height(4.dp))
            Text(
                text = diploma.participantName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(strings.diplomaCampCompleted, fontSize = 14.sp, color = onBg.copy(alpha = 0.65f))
            Spacer(Modifier.height(4.dp))
            Text(
                text = diploma.camp.name ?: "",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = onBg,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = diploma.camp.let { camp ->
                    if (camp.startDate == camp.endDate) {
                        formatMonthDay(camp.startDate, langCode, true)
                    } else {
                        "${formatMonthDay(camp.startDate, langCode, true)} – ${formatMonthDay(camp.endDate, langCode, true)}"
                    }
                },
                fontSize = 14.sp,
                color = onBg.copy(alpha = 0.65f)
            )
        }
    }
}

/** Square Instagram-style grid preview of a diploma; tap to open the full [DiplomaDialog]. */
@Composable
fun DiplomaThumbnail(diploma: DiplomaUiState, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val langCode = AppStrings.currentLanguage.code

    Card(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(1f)
            .semantics { contentDescription = diploma.camp.name ?: "" },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text("🏕️", fontSize = 18.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = diploma.camp.name ?: "",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatMonthDay(diploma.camp.startDate, langCode, true),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
            if (diploma.isNew) {
                NewIndicatorPill(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
    }
}

private fun previewDiploma() = DiplomaUiState(
    camp = CampOccurrence(
        eventId = 1L,
        name = "Letní soustředění",
        startDate = LocalDate(2025, 7, 7),
        endDate = LocalDate(2025, 7, 13),
        seasonStartYear = 2024,
        attendedAllDays = true,
    ),
    participantName = "Jana Nováková",
)

@Preview(name = "DiplomaCard — Light")
@Composable
private fun DiplomaCardPreviewLight() {
    AppTheme(darkTheme = false) {
        DiplomaCardContent(previewDiploma())
    }
}

@Preview(name = "DiplomaCard — Dark")
@Composable
private fun DiplomaCardPreviewDark() {
    AppTheme(darkTheme = true) {
        DiplomaCardContent(previewDiploma())
    }
}

@Preview(name = "DiplomaThumbnail — Light")
@Composable
private fun DiplomaThumbnailPreviewLight() {
    AppTheme(darkTheme = false) {
        Row {
            DiplomaThumbnail(previewDiploma(), modifier = Modifier.width(110.dp))
        }
    }
}

@Preview(name = "DiplomaThumbnail — Dark")
@Composable
private fun DiplomaThumbnailPreviewDark() {
    AppTheme(darkTheme = true) {
        Row {
            DiplomaThumbnail(previewDiploma(), modifier = Modifier.width(110.dp))
        }
    }
}
