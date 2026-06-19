package com.tkolymp.shared.language

data class WidgetStrings(
    val myTrainings: String = "My Trainings",
    val notLoggedIn: String = "Not logged in",
    val noUpcomingEvents: String = "No upcoming trainings",
    val noUpcomingBirthdays: String = "No upcoming birthdays",
    val turns: String = "turns",
    val todayLabel: String = "Today",
    val tomorrowLabel: String = "Tomorrow",
    val inXDays: String = "In %d days",
    val daysAway: String = "days away",
    val dayAway: String = "day away",
) {
    fun formatBirthdayLabel(daysUntil: Int, age: Int): String {
        val prefix = when (daysUntil) {
            0 -> todayLabel
            1 -> tomorrowLabel
            else -> inXDays.replace("%d", daysUntil.toString())
        }
        return "$prefix · $turns $age"
    }
}
