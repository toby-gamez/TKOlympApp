package com.tkolymp.tkolympapp.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tkolymp.shared.campschedule.ScheduleDay

/**
 * Icon buttons (camera + gallery) that let the user photograph or pick a schedule
 * table and turn it into a [ScheduleDay] plus the original photo bytes, entirely
 * on-device (OpenCV grid detection + ML Kit OCR). Android-only: OpenCV and ML Kit have
 * no iOS equivalent in scope, so the iOS actual renders nothing.
 */
@Composable
expect fun CampScheduleUploadButton(
    dayLabel: String,
    enabled: Boolean,
    onScheduleBuilt: (ScheduleDay, ByteArray) -> Unit,
    modifier: Modifier = Modifier
)
