package com.tkolymp.shared.registration

sealed class RegMode {
    object Register : RegMode()
    object Edit : RegMode()
    object Delete : RegMode()
}

data class LessonInput(val trainerId: Int, val lessonCount: Int)
data class RegistrationInput(val personId: String?, val coupleId: String?, val lessons: List<LessonInput>, val note: String? = null)
