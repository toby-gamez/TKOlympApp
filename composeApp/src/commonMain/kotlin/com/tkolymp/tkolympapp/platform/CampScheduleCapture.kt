package com.tkolymp.tkolympapp.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tkolymp.shared.campschedule.ScheduleDay

/**
 * Lets the user photograph (or pick from the gallery) a schedule table and turns it
 * into a [ScheduleDay] entirely on-device (OpenCV grid detection + ML Kit OCR).
 * Android-only: OpenCV and ML Kit have no iOS equivalent in scope, so the iOS actual
 * renders a disabled hint instead.
 */
@Composable
expect fun CampScheduleUploadButton(
    dayLabel: String,
    onScheduleBuilt: (ScheduleDay) -> Unit,
    modifier: Modifier = Modifier
)
