package com.tkolymp.shared.language

data class TutorialStrings(
    val tutorialRowLabel: String = "App Tutorial",
    val tutorialRowSubtitle: String = "A guided tour of the app",

    // Overview sections
    val overviewUpcomingTitle: String = "Upcoming Trainings",
    val overviewUpcomingDesc: String = "Your next training session, listed by trainer. Tap a session card to see the location, time and who else is attending.",
    val overviewBoardTitle: String = "Board Snippet",
    val overviewBoardDesc: String = "A preview of the two most recent club announcements. Tap a post for the full text, or head to the Board tab to see all posts.",
    val overviewCampsTitle: String = "Upcoming Camps",
    val overviewCampsDesc: String = "Camps and club events coming up soon. Tap any item to see details or manage your registration.",
    val overviewBirthdaysTitle: String = "Birthdays",
    val overviewBirthdaysDesc: String = "Upcoming birthdays of club members — so you never miss a chance to celebrate.",
    val overviewStatsTitle: String = "Weekly Stats & Persona",
    val overviewStatsDesc: String = "Dancers see their session count and training minutes for the week, progress toward a weekly goal, and a fun weekly persona card based on their schedule.",

    // Calendar sections
    val calendarMineTitle: String = "Mine Tab",
    val calendarMineDesc: String = "Shows only sessions from your own training groups — your quickest view of the week ahead.",
    val calendarAllTitle: String = "All Tab",
    val calendarAllDesc: String = "Shows every session across all groups. Useful for finding sessions you could join or seeing the full club schedule.",
    val calendarFilterTitle: String = "Filter & Week Navigation",
    val calendarFilterDesc: String = "Tap ⋮ to filter sessions by trainer or location. Use the arrows in the app bar to jump forward or back by week.",

    // Board sections
    val boardListTitle: String = "Announcements",
    val boardListDesc: String = "A scrollable list of every club post from coordinators and trainers. Tap any announcement to read the full text.",
    val boardSearchTitle: String = "Search & Unread Badge",
    val boardSearchDesc: String = "Tap the search icon to find posts by keyword. The Board tab icon shows a badge when there are unread posts.",

    // Events sections
    val eventsPlannedTitle: String = "Planned Events",
    val eventsPlannedDesc: String = "Upcoming camps and club events, sorted by date. Tap any event to see details and sign up.",
    val eventsPastTitle: String = "Past Events",
    val eventsPastDesc: String = "Events that have already taken place. You can still view details or manage an existing registration.",

    // Other (closing step)
    val otherTitle: String = "That's it!",
    val otherDesc: String = "You're all set. Explore the Other tab at your own pace — it has your profile, club members, groups, stats, payments and settings.",

    // Navigation labels
    val next: String = "Next",
    val previous: String = "Back",
    val skip: String = "Skip",
    val done: String = "Done",
    val stepOf: String = "of",
)
