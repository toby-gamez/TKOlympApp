package com.tkolymp.shared.event

/**
 * Safely returns the first trainer name trimmed, or empty string when missing.
 */
fun Event?.firstTrainerOrEmpty(): String = this?.eventTrainersList?.firstOrNull()?.trim() ?: ""
