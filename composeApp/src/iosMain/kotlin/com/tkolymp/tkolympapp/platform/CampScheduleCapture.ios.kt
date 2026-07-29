package com.tkolymp.tkolympapp.platform

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tkolymp.shared.campschedule.ScheduleDay
import com.tkolymp.shared.language.AppStrings

/** Capturing/transcribing a schedule photo requires OpenCV + ML Kit, Android-only. */
@Composable
actual fun CampScheduleUploadButton(
    dayLabel: String,
    enabled: Boolean,
    onScheduleBuilt: (ScheduleDay, ByteArray) -> Unit,
    modifier: Modifier
) {
    Text(
        text = AppStrings.current.campSchedule.notAvailableOnIos,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(16.dp)
    )
}
