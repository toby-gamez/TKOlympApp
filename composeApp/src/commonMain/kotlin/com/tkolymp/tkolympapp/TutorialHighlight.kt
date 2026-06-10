package com.tkolymp.tkolympapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

object TutorialHighlight {
    var rect: Rect? by mutableStateOf(null)
}
