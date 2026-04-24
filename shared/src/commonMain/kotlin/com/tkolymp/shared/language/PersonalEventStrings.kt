package com.tkolymp.shared.language

data class PersonalEventStrings(
    val myTrainings: String = "My trainings",
    val noTrainings: String = "No trainings",
    val newTraining: String = "New training",
    val editTraining: String = "Edit training",
    val defaultTitle: String = "Solo training",
    val deleteTraining: String = "Delete training",
    val confirmDelete: String = "Are you sure you want to delete this training?",
    val trainingTitle: String = "Title",
    val startTime: String = "Start",
    val endTime: String = "End",
    val locationOptional: String = "Location (optional)",
    val descriptionOptional: String = "Description (optional)",
    val trainingTypeLabel: String = "Type",
    val trainingTypeSTT: String = "STT",
    val trainingTypeLAT: String = "LAT",
    val trainingTypeGeneral: String = "General",
    val addReminder: String = "Add reminder",
    val repeatWeekly: String = "Repeat weekly",
    val weekday: String = "Weekday",
    val recurrenceStart: String = "Recurrence start",
    val recurrenceEnd: String = "Recurrence end",
    val saveTraining: String = "Save"
)
